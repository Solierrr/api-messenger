package com.solaria.messenger.core.usecase.group;

import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.solaria.messenger.core.helper.ConversationStateHelper;
import com.solaria.messenger.core.helper.GroupHelper;
import com.solaria.messenger.core.helper.NotificationHelper;
import com.solaria.messenger.core.helper.UserAccessHelper;
import com.solaria.messenger.core.model.Conversation;
import com.solaria.messenger.core.model.enums.ConversationType;
import com.solaria.messenger.core.port.SessionRevoker;
import com.solaria.messenger.core.security.rbac.RbacAuthorizationService;
import com.solaria.messenger.core.support.ConversationMutationGuard;

/**
 * Cobre a marca {@code left_at_version} na saída e na remoção de grupo: ela é gravada depois do CAS de participantes,
 * antes do aviso {@code CONVERSATION_REMOVED}, e só quando o user estava no grupo.
 */
@ExtendWith(MockitoExtension.class)
class RemoveGroupParticipantUseCaseTests {

    private static final String GROUP = "group-1";
    private static final UUID MANAGER = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID MEMBER = UUID.fromString("22222222-2222-2222-2222-222222222222");

    @Mock
    private RbacAuthorizationService rbac;
    @Mock
    private UserAccessHelper users;
    @Mock
    private SessionRevoker sessionRevoker;
    @Mock
    private ConversationStateHelper states;
    @Mock
    private GroupHelper groups;
    @Mock
    private NotificationHelper notifications;

    private RemoveGroupParticipantUseCase useCase;

    @BeforeEach
    void setUp() {
        useCase = new RemoveGroupParticipantUseCase(rbac, users, sessionRevoker, states, groups, notifications,
                new ConversationMutationGuard());
    }

    @Test
    void managerRemovingAMemberMarksTheLeftVersionBeforeTheRemovalNotice() {
        given(rbac.currentUserId()).willReturn(MANAGER);
        given(groups.managedConversation(GROUP)).willReturn(group());

        useCase.execute(GROUP, MEMBER, null);

        InOrder order = inOrder(states, notifications, sessionRevoker);
        order.verify(states).markLeft(MEMBER, GROUP);
        order.verify(notifications).publishRemovedConversation(MEMBER, GROUP);
        order.verify(sessionRevoker).revokeUser(MEMBER);
    }

    @Test
    void memberLeavingOnTheirOwnAlsoMarksTheLeftVersion() {
        given(rbac.currentUserId()).willReturn(MEMBER);
        given(groups.managedConversation(GROUP)).willReturn(group());

        useCase.execute(GROUP, MEMBER, null);

        verify(states).markLeft(MEMBER, GROUP);
        verify(notifications).publishRemovedConversation(MEMBER, GROUP);
    }

    @Test
    void removingSomeoneWhoIsNotInTheGroupMarksNothing() {
        given(rbac.currentUserId()).willReturn(MANAGER);
        Conversation withoutMember = group();
        withoutMember.setParticipantIds(new LinkedHashSet<>(Set.of(MANAGER)));
        given(groups.managedConversation(GROUP)).willReturn(withoutMember);

        useCase.execute(GROUP, MEMBER, null);

        verify(states, never()).markLeft(MEMBER, GROUP);
        verify(notifications, never()).publishRemovedConversation(MEMBER, GROUP);
    }

    private static Conversation group() {
        Conversation group = new Conversation();
        group.setId(GROUP);
        group.setConversationType(ConversationType.GROUP);
        group.setManagerUserId(MANAGER);
        group.setParticipantIds(new LinkedHashSet<>(Set.of(MANAGER, MEMBER)));
        return group;
    }
}
