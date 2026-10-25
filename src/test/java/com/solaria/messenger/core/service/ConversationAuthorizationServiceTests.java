package com.solaria.messenger.core.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.solaria.messenger.core.exception.UnauthorizedAccessException;
import com.solaria.messenger.core.model.CommunityMember;
import com.solaria.messenger.core.model.Conversation;
import com.solaria.messenger.core.model.ProjectCommunity;
import com.solaria.messenger.core.model.enums.CommunityRole;
import com.solaria.messenger.core.model.enums.CommunityStatus;
import com.solaria.messenger.core.repository.CommunityRepository;
import com.solaria.messenger.core.repository.ConversationRepository;

@ExtendWith(MockitoExtension.class)
class ConversationAuthorizationServiceTests {

    private static final UUID VIEWER = UUID.randomUUID();
    private static final UUID MEMBER = UUID.randomUUID();

    @Mock
    private ConversationRepository conversations;

    @Mock
    private CommunityRepository communities;

    private ConversationAuthorizationService authorization() {
        return new ConversationAuthorizationService(conversations, communities, new ConversationMutationGuard());
    }

    private Conversation subgroup() {
        ProjectCommunity community = new ProjectCommunity();
        community.setId("community-1");
        community.setStatus(CommunityStatus.ACTIVE);
        community.setMembers(new ArrayList<>(List.of(
                CommunityMember.of(VIEWER, CommunityRole.VIEWER, Instant.now()),
                CommunityMember.of(MEMBER, CommunityRole.MEMBER, Instant.now()))));
        given(communities.findById("community-1")).willReturn(Optional.of(community));

        Conversation conversation = new Conversation();
        conversation.setId("conversation-1");
        conversation.setCommunityId("community-1");
        return conversation;
    }

    @Test
    void viewersOnlyReadCommunityConversations() {
        Conversation conversation = subgroup();

        assertThatThrownBy(() -> authorization().requireCommunityWriter(conversation, VIEWER))
                .isInstanceOf(UnauthorizedAccessException.class);
    }

    @Test
    void membersWriteInActiveCommunities() {
        Conversation conversation = subgroup();

        assertThatCode(() -> authorization().requireCommunityWriter(conversation, MEMBER)).doesNotThrowAnyException();
    }

    @Test
    void conversationsOutsideCommunitiesAreNotRestricted() {
        assertThatCode(() -> authorization().requireCommunityWriter(new Conversation(), VIEWER))
                .doesNotThrowAnyException();
    }
}
