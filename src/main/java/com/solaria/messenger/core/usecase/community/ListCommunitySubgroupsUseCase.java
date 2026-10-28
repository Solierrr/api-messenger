package com.solaria.messenger.core.usecase.community;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.solaria.messenger.core.contract.response.SubgroupMetadataResponseDTO;
import com.solaria.messenger.core.helper.CommunityHelper;
import com.solaria.messenger.core.helper.LookupHelper;
import com.solaria.messenger.core.mapper.ConversationMapper;
import com.solaria.messenger.core.model.ProjectCommunity;
import com.solaria.messenger.core.model.enums.ConversationType;
import com.solaria.messenger.core.repository.ConversationRepository;
import com.solaria.messenger.core.security.rbac.RbacAuthorizationService;

import static com.solaria.messenger.core.helper.PureRulesHelper.requireMember;

@Service
public class ListCommunitySubgroupsUseCase {

    private final LookupHelper lookup;
    private final RbacAuthorizationService rbac;
    private final ConversationRepository conversationRepository;
    private final CommunityHelper communities;

    public ListCommunitySubgroupsUseCase(LookupHelper lookup, RbacAuthorizationService rbac,
            ConversationRepository conversations, CommunityHelper communities) {
        this.lookup = lookup;
        this.rbac = rbac;
        this.conversationRepository = conversations;
        this.communities = communities;
    }

    /** OWNER/ADMIN veem todos os subgrupos; membro comum vê só os que participa */
    public List<SubgroupMetadataResponseDTO> execute(String id) {
        ProjectCommunity community = lookup.requireCommunity(id);
        UUID actor = rbac.currentUserId();
        requireMember(community, actor);
        return community.isAdmin(actor) ? communities.subgroupMetadata(community.getId())
                : conversationRepository.findByCommunityIdAndConversationTypeAndParticipantIdsContainingOrderByLastInteractionAtDesc(
                        community.getId(), ConversationType.GROUP, actor).stream().map(ConversationMapper::toSubgroupMetadata).toList();
    }
}
