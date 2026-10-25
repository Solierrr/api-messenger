package com.solaria.messenger.core.service.community;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.solaria.messenger.core.exception.MembershipCleanupRequiredException;
import com.solaria.messenger.core.model.CommunityMember;
import com.solaria.messenger.core.model.Conversation;
import com.solaria.messenger.core.model.ProjectCommunity;
import com.solaria.messenger.core.model.enums.CommunityRole;
import com.solaria.messenger.core.model.enums.CommunityStatus;
import com.solaria.messenger.core.model.enums.ConversationType;
import com.solaria.messenger.core.repository.CommunityRepository;
import com.solaria.messenger.core.repository.ConversationRepository;
import com.solaria.messenger.core.service.ConversationAuthorizationService;
import com.solaria.messenger.core.service.ConversationMutationGuard;
import com.solaria.messenger.core.service.conversation.GroupMutationSupport;

@ExtendWith(MockitoExtension.class)
class CommunityGroupCascadeServiceTests {

    private static final UUID MANAGER = UUID.randomUUID();
    private static final UUID MEMBER = UUID.randomUUID();
    private static final UUID FORMER = UUID.randomUUID();

    @Mock
    private ConversationRepository conversations;

    @Mock
    private CommunityRepository communities;

    private CommunityGroupCascadeService cascade;

    @BeforeEach
    void setUp() {
        ConversationAuthorizationService authorization = new ConversationAuthorizationService(conversations,
                communities, new ConversationMutationGuard());
        cascade = new CommunityGroupCascadeService(conversations, authorization,
                new GroupMutationSupport(conversations, authorization));
    }

    private void communityWithout(UUID former) {
        ProjectCommunity community = new ProjectCommunity();
        community.setId("community-1");
        community.setStatus(CommunityStatus.ACTIVE);
        List<CommunityMember> members = new ArrayList<>();
        for (UUID user : List.of(MANAGER, MEMBER, FORMER)) {
            if (!user.equals(former)) members.add(CommunityMember.of(user, CommunityRole.MEMBER, Instant.now()));
        }
        community.setMembers(members);
        given(communities.findById("community-1")).willReturn(Optional.of(community));
    }

    private Conversation subgroup(String id, UUID manager, UUID... participants) {
        Conversation group = new Conversation();
        group.setId(id);
        group.setVersion(1L);
        group.setCommunityId("community-1");
        group.setConversationType(ConversationType.GROUP);
        group.setManagerUserId(manager);
        group.setParticipantIds(new LinkedHashSet<>(List.of(participants)));
        return group;
    }

    private void subgroups(Conversation... groups) {
        given(conversations.findByCommunityIdAndConversationTypeOrderByLastInteractionAtDesc("community-1",
                ConversationType.GROUP)).willReturn(List.of(groups));
    }

    @Test
    void subgroupManagedByWhoLeftIsDissolved() {
        communityWithout(FORMER);
        Conversation group = subgroup("subgroup-1", FORMER, FORMER, MEMBER);
        subgroups(group);
        given(conversations.dissolveIfCurrent(eq("subgroup-1"), eq(1L), any(), eq(2L))).willReturn(1L);
        given(conversations.findById("subgroup-1")).willReturn(Optional.of(group));

        assertThat(cascade.cleanupCommunityRemoval("community-1", FORMER))
                .containsEntry("subgroup-1", Set.of(FORMER, MEMBER));
    }

    @Test
    void participantWhoLeftIsRemovedAndTheManagerStays() {
        communityWithout(FORMER);
        Conversation group = subgroup("subgroup-1", MANAGER, MANAGER, FORMER);
        subgroups(group);
        given(conversations.updateParticipantsIfCurrent(eq("subgroup-1"), eq(1L), eq(Set.of(MANAGER)), eq(MANAGER),
                any(), eq(2L))).willReturn(1L);
        given(conversations.findById("subgroup-1")).willReturn(Optional.of(group));

        assertThat(cascade.cleanupCommunityRemoval("community-1", FORMER)).containsEntry("subgroup-1", Set.of(FORMER));
        verify(conversations, never()).dissolveIfCurrent(any(), any(), any(), any());
    }

    @Test
    void theProjectGeneralConversationIsLeftToItsOwnSync() {
        Conversation general = subgroup("project-general-community-1", FORMER, FORMER, MEMBER);
        general.setProjectGeneral(true);
        subgroups(general);

        assertThat(cascade.cleanupCommunityRemoval("community-1", FORMER)).isEmpty();
        verify(conversations, never()).dissolveIfCurrent(any(), any(), any(), any());
    }

    @Test
    void residueInASubgroupBlocksReadmission() {
        subgroups(subgroup("subgroup-1", MANAGER, MANAGER, FORMER));

        assertThatThrownBy(() -> cascade.requireNoResidualCommunityMembership("community-1", Set.of(FORMER)))
                .isInstanceOf(MembershipCleanupRequiredException.class);
    }
}
