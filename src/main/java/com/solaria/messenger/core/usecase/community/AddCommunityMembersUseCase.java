package com.solaria.messenger.core.usecase.community;

import java.time.Instant;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.solaria.messenger.core.contract.request.CommunityMembersRequestDTO;
import com.solaria.messenger.core.contract.response.CommunityResponseDTO;
import com.solaria.messenger.core.contract.response.RejectedParticipantDTO;
import com.solaria.messenger.core.exception.BusinessRuleException;
import com.solaria.messenger.core.exception.InvalidFieldException;
import com.solaria.messenger.core.helper.CommunityHelper;
import com.solaria.messenger.core.helper.LookupHelper;
import com.solaria.messenger.core.helper.UserAccessHelper;
import com.solaria.messenger.core.model.CommunityMember;
import com.solaria.messenger.core.model.ProjectCommunity;
import com.solaria.messenger.core.model.enums.CommunityRole;
import com.solaria.messenger.core.repository.CommunityRepository;
import com.solaria.messenger.core.security.rbac.RbacAuthorizationService;
import com.solaria.messenger.core.support.ConversationMutationGuard;

import static com.solaria.messenger.core.helper.PureRulesHelper.copyMembers;
import static com.solaria.messenger.core.helper.PureRulesHelper.nextVersion;
import static com.solaria.messenger.core.helper.PureRulesHelper.requireAdmin;
import static com.solaria.messenger.core.helper.PureRulesHelper.requireCommunityActive;
import static com.solaria.messenger.core.helper.PureRulesHelper.requireOwner;
import static com.solaria.messenger.core.mapper.CommunityMapper.toResponse;

/**
 * Caso de uso que adiciona membros a uma comunidade ou altera o papel dos que já são membros.
 *
 * <p>
 * O papel padrão é {@code MEMBER}. O papel {@code OWNER} nunca pode ser atribuído por aqui. Adicionar e promover
 * exigem papel {@code ADMIN} ou {@code OWNER}, e rebaixar um {@code ADMIN} exige ser {@code OWNER}. A comunidade
 * precisa estar ativa.
 * Quem já é membro só tem o papel atualizado, exceto o {@code OWNER}. Quem é novo precisa estar ativo e não pode ter
 * resíduo nos subgrupos. Tudo é revalidado a cada tentativa do CAS.
 * </p>
 *
 * <p>
 * <b>Depende de:</b>
 * </p>
 * <ul>
 *     <li>{@link CommunityRepository} -> grava os membros com CAS.</li>
 *     <li>{@link CommunityHelper} -> confere resíduos nos subgrupos e grava a comunidade.</li>
 *     <li>{@link LookupHelper} -> carrega a comunidade.</li>
 *     <li>{@link ConversationMutationGuard} -> serializa a operação na JVM.</li>
 *     <li>{@link RbacAuthorizationService} -> identifica o ator.</li>
 *     <li>{@link UserAccessHelper} -> confere os novos membros ativos.</li>
 * </ul>
 *
 * <p>
 * Principal operação disponibilizada:
 * </p>
 * <ul>
 *     <li>{@link #execute(String, CommunityMembersRequestDTO)} -> adiciona membros ou troca papéis.</li>
 * </ul>
 */
@Service
public class AddCommunityMembersUseCase {

    private final CommunityRepository communityRepository;
    private final CommunityHelper communities;
    private final LookupHelper lookup;
    private final ConversationMutationGuard guard;
    private final RbacAuthorizationService rbac;
    private final UserAccessHelper users;

    public AddCommunityMembersUseCase(CommunityRepository communityRepository, CommunityHelper communities,
            LookupHelper lookup, ConversationMutationGuard guard, RbacAuthorizationService rbac, UserAccessHelper users) {
        this.communityRepository = communityRepository;
        this.communities = communities;
        this.lookup = lookup;
        this.guard = guard;
        this.rbac = rbac;
        this.users = users;
    }

    /**
     * Adiciona membros à comunidade ou altera o papel dos que já são membros.
     *
     * @param id identificador da comunidade.
     * @param dto usuários e papel desejado.
     * @return comunidade com os membros atualizados.
     * @throws InvalidFieldException caso o papel pedido seja {@code OWNER}.
     * @throws UnauthorizedAccessException caso o ator não tenha papel suficiente para o papel pedido.
     * @throws ConcurrentUpdateException caso a comunidade mude durante a operação e o CAS não consiga gravar depois das
     *     tentativas.
     */
    public CommunityResponseDTO execute(String id, CommunityMembersRequestDTO dto) {
        return guard.execute(() -> addMembersLocked(id, dto));
    }

    /** Valida papel e convidados antes do CAS e revalida tudo a cada nova tentativa */
    private CommunityResponseDTO addMembersLocked(String id, CommunityMembersRequestDTO dto) {
        ProjectCommunity community = lookup.requireCommunity(id);
        UUID actor = rbac.currentUserId();

        // papel padrão é MEMBER; OWNER nunca pode ser atribuído por aqui
        CommunityRole role = dto.getRole() != null ? dto.getRole() : CommunityRole.MEMBER;
        if (role == CommunityRole.OWNER) {
            throw new InvalidFieldException("Não é possível atribuir o papel OWNER.");
        }
        requireMemberAddition(community, actor);

        // separa quem já é membro (só troca papel) de quem é novo (precisa estar ativo e sem resíduo nos subgrupos)
        Set<UUID> existingIds = community.memberIds();
        Set<UUID> existingRequestedIds = new LinkedHashSet<>(dto.getUserIds());
        existingRequestedIds.retainAll(existingIds);
        Set<UUID> newMemberIds = newMembers(dto.getUserIds(), existingIds);
        List<RejectedParticipantDTO> rejected = users.ineligibleUsers(newMemberIds);
        Set<UUID> admitted = new LinkedHashSet<>(dto.getUserIds());
        admitted.remove(null);
        rejected.forEach(user -> admitted.remove(user.userId()));
        if (admitted.isEmpty()) throw new BusinessRuleException("Nenhum convidado pode entrar na comunidade.");
        communities.requireNoResidualCommunityMembership(id, newMembers(admitted, existingIds));

        CommunityResponseDTO response = toResponse(communities.mutateCommunity(community, latest -> {
            // revalida na versão mais recente: quem era membro na leitura precisa continuar sendo
            requireMemberAddition(latest, actor);
            if (!latest.memberIds().containsAll(existingRequestedIds)) {
                throw communities.concurrentModification();
            }

            // atualiza o papel de quem já existe (menos o OWNER) e adiciona os novos
            List<CommunityMember> updatedMembers = copyMembers(latest.getMembers());
            Instant now = Instant.now();
            for (UUID userId : admitted) {
                updatedMembers.stream().filter(member -> member.getUserId().equals(userId)).findFirst()
                        .ifPresentOrElse(existing -> {
                            if (existing.getRole() == CommunityRole.ADMIN && role != CommunityRole.ADMIN) {
                                requireOwner(latest, actor);
                            }
                            if (existing.getRole() != CommunityRole.OWNER) existing.setRole(role);
                        }, () -> updatedMembers.add(CommunityMember.of(userId, role, now)));
            }

            // confere de novo o resíduo nos subgrupos e grava só se a versão não mudou
            communities.requireNoResidualCommunityMembership(id, newMembers(admitted, latest.memberIds()));
            return communityRepository.updateMembersIfCurrent(latest.getId(), latest.getVersion(), updatedMembers,
                    Instant.now(), nextVersion(latest.getVersion()));
        }));
        response.setRejectedParticipants(rejected);
        return response;
    }

    /** Adicionar e promover exigem ADMIN; a comunidade precisa estar ativa */
    private void requireMemberAddition(ProjectCommunity community, UUID actor) {
        requireAdmin(community, actor);
        requireCommunityActive(community);
    }

    /** Ids pedidos que ainda não são membros (sem nulos) */
    private static Set<UUID> newMembers(Collection<UUID> requested, Set<UUID> current) {
        Set<UUID> fresh = new LinkedHashSet<>(requested);
        fresh.removeAll(current);
        fresh.remove(null);
        return fresh;
    }
}
