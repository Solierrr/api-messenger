package com.solaria.messenger.core.usecase.community;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.solaria.messenger.core.contract.request.CommunityRequestDTO;
import com.solaria.messenger.core.contract.response.CommunityResponseDTO;
import com.solaria.messenger.core.exception.DuplicateResourceException;
import com.solaria.messenger.core.helper.UserAccessHelper;
import com.solaria.messenger.core.integration.core.ProjectAuthorityClient;
import com.solaria.messenger.core.model.CommunityMember;
import com.solaria.messenger.core.model.ProjectCommunity;
import com.solaria.messenger.core.model.enums.CommunityRole;
import com.solaria.messenger.core.model.enums.CommunityStatus;
import com.solaria.messenger.core.repository.CommunityRepository;
import com.solaria.messenger.core.security.rbac.RbacAuthorizationService;

import static com.solaria.messenger.core.mapper.CommunityMapper.toResponse;

/**
 * Caso de uso que cria a comunidade de um projeto.
 *
 * <p>
 * Só o demandante do projeto pode criar, e cada projeto tem uma comunidade só. O criador entra como {@code OWNER}.
 * Os membros iniciais entram como {@code MEMBER}, sem nulos e sem o próprio criador, e todos precisam estar ativos.
 * </p>
 *
 * <p>
 * <b>Depende de:</b>
 * </p>
 * <ul>
 *     <li>{@link CommunityRepository} -> verifica se o projeto já tem comunidade e grava a nova.</li>
 *     <li>{@link RbacAuthorizationService} -> identifica o criador.</li>
 *     <li>{@link ProjectAuthorityClient} -> confirma que o user é o demandante do projeto.</li>
 *     <li>{@link UserAccessHelper} -> confere os membros iniciais ativos.</li>
 * </ul>
 *
 * <p>
 * Principal operação disponibilizada:
 * </p>
 * <ul>
 *     <li>{@link #execute(CommunityRequestDTO)} -> cria a comunidade do projeto.</li>
 * </ul>
 */
@Service
public class CreateCommunityUseCase {

    private final CommunityRepository communityRepository;
    private final RbacAuthorizationService rbac;
    private final ProjectAuthorityClient projectAuthorityClient;
    private final UserAccessHelper users;

    public CreateCommunityUseCase(CommunityRepository communityRepository, RbacAuthorizationService rbac,
            ProjectAuthorityClient projectAuthorityClient, UserAccessHelper users) {
        this.communityRepository = communityRepository;
        this.rbac = rbac;
        this.projectAuthorityClient = projectAuthorityClient;
        this.users = users;
    }

    /**
     * Cria a comunidade do projeto.
     *
     * @param dto projeto e membros iniciais da comunidade.
     * @return comunidade criada.
     * @throws DuplicateResourceException caso o projeto já tenha uma comunidade.
     */
    public CommunityResponseDTO execute(CommunityRequestDTO dto) {
        UUID currentUserId = rbac.currentUserId();

        // só o demandante do projeto cria, e cada projeto tem uma comunidade só
        projectAuthorityClient.requireRequester(dto.getProjectId(), currentUserId);
        if (communityRepository.existsByProjectId(dto.getProjectId())) {
            throw new DuplicateResourceException("Já existe uma comunidade para o projeto: " + dto.getProjectId());
        }

        // membros iniciais sem nulos e sem o próprio criador, todos precisam estar ativos
        Set<UUID> memberIds = new LinkedHashSet<>();
        if (dto.getMemberIds() != null) {
            dto.getMemberIds().stream()
                    .filter(Objects::nonNull)
                    .filter(id -> !id.equals(currentUserId))
                    .forEach(memberIds::add);
        }
        users.requireActiveUsers(memberIds);

        // monta a lista com o criador como OWNER e os demais como MEMBER
        Instant now = Instant.now();
        List<CommunityMember> members = new ArrayList<>();
        members.add(CommunityMember.of(currentUserId, CommunityRole.OWNER, now));
        memberIds.forEach(id -> members.add(CommunityMember.of(id, CommunityRole.MEMBER, now)));

        // monta e salva a comunidade ativa
        ProjectCommunity community = new ProjectCommunity();
        community.setProjectId(dto.getProjectId());
        community.setMembers(members);
        community.setStatus(CommunityStatus.ACTIVE);
        community.setCreatedBy(currentUserId);
        community.setCreatedAt(now);
        community.setUpdatedAt(now);
        return toResponse(communityRepository.save(community));
    }
}
