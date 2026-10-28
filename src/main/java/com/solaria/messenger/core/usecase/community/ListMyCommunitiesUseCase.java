package com.solaria.messenger.core.usecase.community;

import java.util.List;

import org.springframework.stereotype.Service;

import com.solaria.messenger.core.contract.response.CommunityResponseDTO;
import com.solaria.messenger.core.mapper.CommunityMapper;
import com.solaria.messenger.core.repository.CommunityRepository;
import com.solaria.messenger.core.security.rbac.RbacAuthorizationService;

@Service
public class ListMyCommunitiesUseCase {

    private final CommunityRepository communityRepository;
    private final RbacAuthorizationService rbac;

    public ListMyCommunitiesUseCase(CommunityRepository communityRepository, RbacAuthorizationService rbac) {
        this.communityRepository = communityRepository;
        this.rbac = rbac;
    }

    /** Lista as comunidades do user autenticado */
    public List<CommunityResponseDTO> execute() {
        return communityRepository.findAllByMemberId(rbac.currentUserId()).stream()
                .map(CommunityMapper::toResponse)
                .toList();
    }
}
