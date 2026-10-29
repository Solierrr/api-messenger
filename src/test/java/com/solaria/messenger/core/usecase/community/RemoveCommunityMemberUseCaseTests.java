package com.solaria.messenger.core.usecase.community;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.solaria.messenger.core.helper.CommunityHelper;
import com.solaria.messenger.core.helper.ConversationStateHelper;
import com.solaria.messenger.core.helper.LookupHelper;
import com.solaria.messenger.core.helper.NotificationHelper;
import com.solaria.messenger.core.helper.UserAccessHelper;
import com.solaria.messenger.core.model.CommunityMember;
import com.solaria.messenger.core.model.Conversation;
import com.solaria.messenger.core.model.ProjectCommunity;
import com.solaria.messenger.core.model.enums.CommunityRole;
import com.solaria.messenger.core.model.enums.CommunityStatus;
import com.solaria.messenger.core.port.SessionRevoker;
import com.solaria.messenger.core.repository.CommunityRepository;
import com.solaria.messenger.core.security.rbac.RbacAuthorizationService;
import com.solaria.messenger.core.support.ConversationMutationGuard;

/**
 * Cobre a marca {@code left_at_version} na cascata da comunidade: cada subgrupo em que o user participava recebe a
 * marca antes do aviso {@code CONVERSATION_REMOVED}, e subgrupos em que ele não estava ficam de fora.
 */
@ExtendWith(MockitoExtension.class)
class RemoveCommunityMemberUseCaseTests {

    private static final String COMMUNITY = "community-1";
    private static final UUID OWNER = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID MEMBER = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Mock
    private CommunityRepository communityRepository;
    @Mock
    private CommunityHelper communities;
    @Mock
    private LookupHelper lookup;
    @Mock
    private NotificationHelper notifications;
    @Mock
    private RbacAuthorizationService rbac;
    @Mock
    private SessionRevoker sessionRevoker;
    @Mock
    private UserAccessHelper users;
    @Mock
    private ConversationStateHelper states;

    private RemoveCommunityMemberUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new RemoveCommunityMemberUseCase(communityRepository, communities, lookup, notifications,
                new ConversationMutationGuard(), rbac, sessionRevoker, users, states);
    }

    @Test
    void marksTheLeftVersionOfEachSubgroupTheMemberWasInBeforeTheRemovalNotice() {
        ProjectCommunity before = community(true);
        Conversation inFirst = subgroup("sub-1", Set.of(OWNER, MEMBER));
        Conversation inSecond = subgroup("sub-2", Set.of(MEMBER));
        Conversation without = subgroup("sub-3", Set.of(OWNER));
        given(rbac.currentUserId()).willReturn(OWNER);
        given(lookup.requireCommunity(COMMUNITY)).willReturn(before);
        given(communities.validateCommunityRemoval(eq(before), eq(OWNER), eq(MEMBER), anyMap()))
                .willReturn(List.of(inFirst, inSecond, without));
        given(communities.mutateCommunity(eq(before), any())).willReturn(community(false));

        useCase.execute(COMMUNITY, MEMBER, null);

        InOrder order = inOrder(states, notifications);
        order.verify(states).markLeft(MEMBER, "sub-1");
        order.verify(notifications).publishRemovedConversation(MEMBER, "sub-1");
        order.verify(states).markLeft(MEMBER, "sub-2");
        order.verify(notifications).publishRemovedConversation(MEMBER, "sub-2");
        verify(states, never()).markLeft(MEMBER, "sub-3");
        verify(communities).cleanupCommunityRemoval(COMMUNITY, OWNER, MEMBER);
    }

    @Test
    void marksNothingWhileTheMemberIsStillInTheCommunityAfterTheWrite() {
        // o CAS da comunidade não removeu o user (concorrência): ele não perdeu o acesso, então não há marca
        ProjectCommunity before = community(true);
        given(rbac.currentUserId()).willReturn(OWNER);
        given(lookup.requireCommunity(COMMUNITY)).willReturn(before);
        given(communities.validateCommunityRemoval(eq(before), eq(OWNER), eq(MEMBER), anyMap()))
                .willReturn(List.of(subgroup("sub-1", Set.of(MEMBER))));
        given(communities.mutateCommunity(eq(before), any())).willReturn(community(true));

        useCase.execute(COMMUNITY, MEMBER, null);

        verify(states, never()).markLeft(any(), any());
    }

    private static ProjectCommunity community(boolean withMember) {
        ProjectCommunity community = new ProjectCommunity();
        community.setId(COMMUNITY);
        community.setStatus(CommunityStatus.ACTIVE);
        List<CommunityMember> members = new ArrayList<>();
        members.add(CommunityMember.of(OWNER, CommunityRole.OWNER, null));
        if (withMember) members.add(CommunityMember.of(MEMBER, CommunityRole.MEMBER, null));
        community.setMembers(members);
        return community;
    }

    private static Conversation subgroup(String id, Set<UUID> participants) {
        Conversation group = new Conversation();
        group.setId(id);
        group.setCommunityId(COMMUNITY);
        group.setParticipantIds(new LinkedHashSet<>(participants));
        return group;
    }
}
