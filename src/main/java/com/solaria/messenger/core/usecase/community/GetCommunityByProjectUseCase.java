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

@Service
public class GetCommunityByProjectUseCase {

    private final CommunityRepository communityRepository;
    private final RbacAuthorizationService rbac;

    public GetCommunityByProjectUseCase(CommunityRepository communityRepository, RbacAuthorizationService rbac) {
        this.communityRepository = communityRepository;
        this.rbac = rbac;
    }

    /** Busca a comunidade de um projeto da qual o user é membro */
    public CommunityResponseDTO execute(UUID projectId) {
        ProjectCommunity community = communityRepository.findByProjectId(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Comunidade não encontrada para o projeto: " + projectId));
        requireMember(community, rbac.currentUserId());
        return toResponse(community);
    }
}
