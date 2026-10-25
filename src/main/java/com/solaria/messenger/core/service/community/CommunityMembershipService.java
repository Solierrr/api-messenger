package com.solaria.messenger.core.service.community;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.solaria.messenger.core.contract.request.CommunityMemberRemovalRequestDTO;
import com.solaria.messenger.core.contract.request.CommunityMembersRequestDTO;
import com.solaria.messenger.core.contract.request.SubgroupSuccessorRequestDTO;
import com.solaria.messenger.core.contract.response.CommunityResponseDTO;
import com.solaria.messenger.core.exception.BusinessException;
import com.solaria.messenger.core.exception.BusinessRuleException;
import com.solaria.messenger.core.exception.ConcurrentUpdateException;
import com.solaria.messenger.core.exception.InvalidFieldException;
import com.solaria.messenger.core.exception.RemovalIncompleteException;
import com.solaria.messenger.core.exception.ResourceNotFoundException;
import com.solaria.messenger.core.model.CommunityMember;
import com.solaria.messenger.core.model.Conversation;
import com.solaria.messenger.core.model.ProjectCommunity;
import com.solaria.messenger.core.model.enums.CommunityRole;
import com.solaria.messenger.core.model.enums.CommunityStatus;
import com.solaria.messenger.core.repository.CommunityRepository;
import com.solaria.messenger.core.security.rbac.RbacAuthorizationService;
import com.solaria.messenger.core.service.ConversationMutationGuard;
import com.solaria.messenger.core.service.OptimisticMutation;
import com.solaria.messenger.core.service.UserAccessService;
import com.solaria.messenger.core.service.conversation.ConversationChangeNotifier;
import com.solaria.messenger.core.service.port.SessionRevoker;

import static com.solaria.messenger.core.mapper.CommunityMapper.toResponse;
import static com.solaria.messenger.core.security.rbac.CommunityPolicy.requireAdmin;
import static com.solaria.messenger.core.security.rbac.CommunityPolicy.requireOwner;
import static com.solaria.messenger.core.security.rbac.CommunityPolicy.requireRemovalAuthority;

/**
 * Service de membros da comunidade
 *
 * <p>
 * Adiciona membros, troca papéis e remove membros, levando a remoção para os subgrupos.
 * Toda escrita passa pelo guard local e pelo controle de versão (CAS) da comunidade.
 * </p>
 * <ul>
 *     <li>{@link #addMembers(String, CommunityMembersRequestDTO)} -> adiciona membros ou altera o papel deles.</li>
 *     <li>{@link #removeMember(String, UUID, CommunityMemberRemovalRequestDTO)} -> remove o membro da comunidade e dos subgrupos.</li>
 * </ul>
 */
@Service
public class CommunityMembershipService {

    private static final Logger log = LoggerFactory.getLogger(CommunityMembershipService.class);
    private final CommunityRepository communityRepository;
    private final CommunityGroupCascadeService cascade;
    private final ConversationChangeNotifier stateEvents;
    private final ConversationMutationGuard guard;
    private final RbacAuthorizationService rbac;
    private final SessionRevoker sessionRevoker;
    private final UserAccessService userAccessService;

    public CommunityMembershipService(CommunityRepository communityRepository, CommunityGroupCascadeService cascade,
            ConversationChangeNotifier stateEvents, ConversationMutationGuard guard, RbacAuthorizationService rbac,
            SessionRevoker sessionRevoker, UserAccessService userAccessService) {
        this.communityRepository = communityRepository;
        this.cascade = cascade;
        this.stateEvents = stateEvents;
        this.guard = guard;
        this.rbac = rbac;
        this.sessionRevoker = sessionRevoker;
        this.userAccessService = userAccessService;
    }

    /** Adiciona membros ou altera o papel deles, dentro do guard local */
    public CommunityResponseDTO addMembers(String id, CommunityMembersRequestDTO dto) {
        return guard.execute(() -> addMembersLocked(id, dto));
    }

    /** Valida papel e convidados antes do CAS e revalida tudo a cada nova tentativa */
    private CommunityResponseDTO addMembersLocked(String id, CommunityMembersRequestDTO dto) {
        ProjectCommunity community = requireEntityById(id);
        UUID actor = rbac.currentUserId();

        // papel padrão é MEMBER; OWNER nunca pode ser atribuído por aqui
        CommunityRole role = dto.getRole() != null ? dto.getRole() : CommunityRole.MEMBER;
        if (role == CommunityRole.OWNER) {
            throw new InvalidFieldException("Não é possível atribuir o papel OWNER.");
        }
        requireMemberAddition(community, actor, role);

        // separa quem já é membro (só troca papel) de quem é novo (precisa estar ativo e sem resíduo nos subgrupos)
        Set<UUID> existingIds = community.memberIds();
        Set<UUID> existingRequestedIds = new LinkedHashSet<>(dto.getUserIds());
        existingRequestedIds.retainAll(existingIds);
        Set<UUID> newMemberIds = newMembers(dto.getUserIds(), existingIds);
        cascade.requireNoResidualCommunityMembership(id, newMemberIds);
        userAccessService.requireActiveUsers(newMemberIds);

        return toResponse(mutateCommunity(community, latest -> {
            // revalida na versão mais recente: quem era membro na leitura precisa continuar sendo
            requireMemberAddition(latest, actor, role);
            if (!latest.memberIds().containsAll(existingRequestedIds)) {
                throw concurrentModification();
            }

            // atualiza o papel de quem já existe (menos o OWNER) e adiciona os novos
            List<CommunityMember> updatedMembers = copyMembers(latest.getMembers());
            Instant now = Instant.now();
            for (UUID userId : dto.getUserIds()) {
                if (userId == null) continue;
                updatedMembers.stream().filter(member -> member.getUserId().equals(userId)).findFirst()
                        .ifPresentOrElse(existing -> {
                            if (existing.getRole() != CommunityRole.OWNER) existing.setRole(role);
                        }, () -> updatedMembers.add(CommunityMember.of(userId, role, now)));
            }

            // confere de novo o resíduo nos subgrupos e grava só se a versão não mudou
            cascade.requireNoResidualCommunityMembership(id, newMembers(dto.getUserIds(), latest.memberIds()));
            return communityRepository.updateMembersIfCurrent(latest.getId(), latest.getVersion(), updatedMembers,
                    Instant.now(), OptimisticMutation.nextVersion(latest.getVersion()));
        }));
    }

    /**
     * Remove o membro da comunidade e dos subgrupos, retomando do estado atual se for repetido.
     * Não devolve a lista de membros, porque quem chama pode ter acabado de sair.
     */
    public void removeMember(String id, UUID userId, CommunityMemberRemovalRequestDTO dto) {
        Map<String, UUID> successors = successorMap(dto);
        Set<String> affectedConversations = new LinkedHashSet<>();
        try {
            guard.execute(() -> {
                // ator ativo, sucessores completos e permissão para remover
                UUID actor = rbac.currentUserId();
                userAccessService.requireActiveActor(actor);
                ProjectCommunity initial = requireEntityById(id);
                Map<String, UUID> requested = new LinkedHashMap<>(successors);
                if (requested.entrySet().stream().anyMatch(entry -> entry.getKey() == null || entry.getKey().isBlank()
                        || entry.getValue() == null)) {
                    throw new InvalidFieldException("Sucessores precisam informar conversa e usuário.");
                }
                requireRemovalAuthority(initial, actor, userId);

                // valida o plano nos subgrupos e anota as conversas que serão afetadas
                List<Conversation> groups = cascade.validateCommunityRemoval(initial, actor, userId, requested);
                groups.forEach(group -> affectedConversations.add(group.getId()));
                boolean affected = initial.isMember(userId) || groups.stream().anyMatch(group -> group.hasParticipant(userId));

                try {
                    // 1. passa a gestão dos subgrupos para os sucessores
                    cascade.prepareCommunityRemoval(initial, actor, userId, requested, groups);

                    // 2. tira o user da comunidade
                    ProjectCommunity after = mutateCommunity(initial, latest -> {
                        requireRemovalAuthority(latest, actor, userId);
                        if (!latest.isMember(userId)) return null;
                        List<CommunityMember> survivors = copyMembers(latest.getMembers());
                        survivors.removeIf(member -> member.getUserId().equals(userId));
                        return communityRepository.updateMembersIfCurrent(latest.getId(), latest.getVersion(), survivors,
                                Instant.now(), OptimisticMutation.nextVersion(latest.getVersion()));
                    });

                    // 3. avisa o user que ele perdeu os subgrupos
                    if (!after.isMember(userId)) {
                        groups.stream().filter(group -> group.hasParticipant(userId))
                                .forEach(group -> stateEvents.publishRemovedConversation(userId, group.getId()));
                    }

                    // 4. tira o user dos subgrupos
                    cascade.cleanupCommunityRemoval(id, actor, userId);

                } catch (BusinessException business) {
                    throw business;
                } catch (RuntimeException failure) {
                    // falha técnica no meio da cascata: o DELETE pode ser repetido e continua de onde parou
                    log.error("Falha na remoção de membro; o DELETE pode ser repetido (communityId={}, userId={})",
                            id, userId, failure);
                    BusinessException incomplete = new RemovalIncompleteException();
                    incomplete.addSuppressed(failure);
                    throw incomplete;
                } finally {
                    // derruba as sessões do user para ele perder as inscrições já abertas
                    if (affected) sessionRevoker.revokeUser(userId);
                }
                return null;
            });
        } finally {
            stateEvents.publishConversationsChanged(affectedConversations);
        }
    }

    /** Converte o corpo opcional em mapa conversa -> sucessor, recusando entradas incompletas ou repetidas */
    private static Map<String, UUID> successorMap(CommunityMemberRemovalRequestDTO dto) {
        if (dto == null || dto.successors() == null || dto.successors().isEmpty()) {
            return Map.of();
        }

        Map<String, UUID> result = new LinkedHashMap<>();
        for (SubgroupSuccessorRequestDTO successor : dto.successors()) {
            if (successor == null || successor.conversationId() == null || successor.successorUserId() == null) {
                throw new InvalidFieldException("Cada sucessor precisa informar conversa e usuário");
            }

            // id do sucessor em formato inválido vira erro de campo
            UUID successorUserId;
            try {
                successorUserId = successor.successorUserIdValue();
            } catch (IllegalArgumentException exception) {
                throw new InvalidFieldException(exception.getMessage());
            }

            if (result.containsKey(successor.conversationId())) {
                throw new InvalidFieldException("A conversa não pode aparecer mais de uma vez nos sucessores");
            }
            result.put(successor.conversationId(), successorUserId);
        }
        return Collections.unmodifiableMap(result);
    }

    /** Dar ADMIN exige OWNER; os outros papéis exigem ADMIN; a comunidade precisa estar ativa */
    private void requireMemberAddition(ProjectCommunity community, UUID actor, CommunityRole role) {
        if (role == CommunityRole.ADMIN) {
            requireOwner(community, actor);
        } else {
            requireAdmin(community, actor);
        }
        if (community.getStatus() != CommunityStatus.ACTIVE) {
            throw new BusinessRuleException("A comunidade está arquivada.");
        }
    }

    /** Ids pedidos que ainda não são membros (sem nulos) */
    private static Set<UUID> newMembers(Collection<UUID> requested, Set<UUID> current) {
        Set<UUID> fresh = new LinkedHashSet<>(requested);
        fresh.removeAll(current);
        fresh.remove(null);
        return fresh;
    }

    /** Aplica a escrita com CAS; se a versão mudou, relê a comunidade e tenta de novo */
    private ProjectCommunity mutateCommunity(ProjectCommunity initial, Function<ProjectCommunity, Long> mutation) {
        return OptimisticMutation.apply(initial, mutation, () -> requireEntityById(initial.getId()),
                this::concurrentModification);
    }

    private BusinessException concurrentModification() {
        return new ConcurrentUpdateException("A comunidade mudou durante a operação.");
    }

    private ProjectCommunity requireEntityById(String id) {
        return communityRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Comunidade não encontrada com id: " + id));
    }

    /** Cópia dos membros, para montar a escrita sem alterar o documento lido */
    private static List<CommunityMember> copyMembers(List<CommunityMember> members) {
        return members == null ? new ArrayList<>() : members.stream()
                .map(member -> CommunityMember.of(member.getUserId(), member.getRole(), member.getJoinedAt()))
                .collect(Collectors.toCollection(ArrayList::new));
    }
}
