package com.solaria.messenger.core.usecase.community;

import java.util.UUID;

import org.springframework.stereotype.Service;

import com.solaria.messenger.core.contract.response.CommunityResponseDTO;
import com.solaria.messenger.core.exception.ResourceNotFoundException;
import com.solaria.messenger.core.model.ProjectCommunity;
import com.solaria.messenger.core.repository.CommunityRepository;
import com.solaria.messenger.core.security.rbac.RbacAuthorizationService;

import static com.solaria.messenger.core.helper.PureRulesHelper.requireMember;
import static com.solaria.messenger.core.mapper.CommunityMapper.toResponse;

/**
 * Caso de uso que busca a comunidade de um projeto.
 *
 * <p>
 * A comunidade só é devolvida se o user atual for membro dela.
 * </p>
 *
 * <p>
 * <b>Depende de:</b>
 * </p>
 * <ul>
 *     <li>{@link CommunityRepository} -> busca a comunidade pelo projeto.</li>
 *     <li>{@link RbacAuthorizationService} -> identifica o user atual.</li>
 * </ul>
 *
 * <p>
 * Principal operação disponibilizada:
 * </p>
 * <ul>
 *     <li>{@link #execute(UUID)} -> busca a comunidade do projeto.</li>
 * </ul>
 */
@Service
public class GetCommunityByProjectUseCase {

    private final CommunityRepository communityRepository;
    private final RbacAuthorizationService rbac;

    public GetCommunityByProjectUseCase(CommunityRepository communityRepository, RbacAuthorizationService rbac) {
        this.communityRepository = communityRepository;
        this.rbac = rbac;
    }

    /**
     * Busca a comunidade do projeto.
     *
     * @param projectId identificador do projeto.
     * @return comunidade encontrada.
     * @throws ResourceNotFoundException caso o projeto não tenha comunidade.
     * @throws UnauthorizedAccessException caso o user atual não seja membro da comunidade.
     */
    public CommunityResponseDTO execute(UUID projectId) {
        ProjectCommunity community = communityRepository.findByProjectId(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Comunidade não encontrada para o projeto: " + projectId));
        requireMember(community, rbac.currentUserId());
        return toResponse(community);
    }
}
