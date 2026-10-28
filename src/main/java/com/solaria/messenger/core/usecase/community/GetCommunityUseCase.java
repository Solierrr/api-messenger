package com.solaria.messenger.core.usecase.community;

import org.springframework.stereotype.Service;

import com.solaria.messenger.core.contract.response.CommunityResponseDTO;
import com.solaria.messenger.core.helper.LookupHelper;
import com.solaria.messenger.core.model.ProjectCommunity;
import com.solaria.messenger.core.security.rbac.RbacAuthorizationService;

import static com.solaria.messenger.core.helper.PureRulesHelper.requireMember;
import static com.solaria.messenger.core.mapper.CommunityMapper.toResponse;

@Service
public class GetCommunityUseCase {

    private final LookupHelper lookup;
    private final RbacAuthorizationService rbac;

    public GetCommunityUseCase(LookupHelper lookup, RbacAuthorizationService rbac) {
        this.lookup = lookup;
        this.rbac = rbac;
    }

    /** Busca uma comunidade da qual o user é membro */
    public CommunityResponseDTO execute(String id) {
        ProjectCommunity community = lookup.requireCommunity(id);
        requireMember(community, rbac.currentUserId());
        return toResponse(community);
    }
}
