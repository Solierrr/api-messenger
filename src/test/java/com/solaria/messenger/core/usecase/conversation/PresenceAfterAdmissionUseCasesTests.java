package com.solaria.messenger.core.usecase.conversation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.solaria.messenger.core.contract.request.DirectConversationRequestDTO;
import com.solaria.messenger.core.helper.ConversationAdmissionHelper;
import com.solaria.messenger.core.helper.ConversationStateHelper;
import com.solaria.messenger.core.helper.GroupHelper;
import com.solaria.messenger.core.helper.LookupHelper;
import com.solaria.messenger.core.helper.NotificationHelper;
import com.solaria.messenger.core.helper.UserAccessHelper;
import com.solaria.messenger.core.model.Conversation;
import com.solaria.messenger.core.model.enums.ConversationType;
import com.solaria.messenger.core.repository.ConversationRepository;
import com.solaria.messenger.core.repository.MessageRepository;
import com.solaria.messenger.core.security.rbac.RbacAuthorizationService;
import com.solaria.messenger.core.support.ConversationMutationGuard;
import com.solaria.messenger.core.usecase.group.AddGroupParticipantsUseCase;

/**
 * Cobre quem avisa a presença quando uma conversa ganha gente: direta nova, grupo novo e participante adicionado
 * chamam {@code publishPresenceOf} depois de gravar e fora do guard; sem mudança, nada é avisado.
 */
class PresenceAfterAdmissionUseCasesTests {

    private static final UUID A = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID B = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private static final UUID C = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");

    private RbacAuthorizationService rbac;
    private UserAccessHelper users;
    private ConversationAdmissionHelper admissions;
    private NotificationHelper notifications;
    private ConversationMutationGuard guard;
    private final AtomicBoolean calledInsideGuard = new AtomicBoolean();

    @BeforeEach
    void setUp() {
        rbac = mock(RbacAuthorizationService.class);
        users = mock(UserAccessHelper.class);
        admissions = mock(ConversationAdmissionHelper.class);
        notifications = mock(NotificationHelper.class);
        guard = new ConversationMutationGuard();
        given(rbac.currentUserId()).willReturn(A);
        given(users.ineligibleUsers(any())).willReturn(List.of());
        doAnswer(call -> {
            calledInsideGuard.set(guard.isHeldByCurrentThread());
            return null;
        }).when(notifications).publishPresenceOf(anyString());
    }

    @Test
    void newDirectConversationPublishesPresenceOutsideTheGuard() {
        ConversationRepository conversations = mock(ConversationRepository.class);
        given(conversations.findByConversationTypeAndDirectKey(any(), any())).willReturn(Optional.empty());
        given(admissions.persistNew(any())).willReturn(conversation("d1", ConversationType.DIRECT, A, B));
        var useCase = new CreateDirectConversationUseCase(conversations, rbac, users, mock(ConversationStateHelper.class),
                admissions, mock(MessageRepository.class), notifications, guard);
        DirectConversationRequestDTO request = new DirectConversationRequestDTO();
        request.setRecipientId(B);

        useCase.execute(request);

        verify(notifications).publishPresenceOf("d1");
        assertThat(calledInsideGuard).isFalse();
    }

    @Test
    void newGroupPublishesPresenceOutsideTheGuard() {
        given(admissions.persistNew(any())).willReturn(conversation("g1", ConversationType.GROUP, A, B));
        var useCase = new CreateGroupConversationUseCase(rbac, users, mock(LookupHelper.class), admissions,
                notifications, guard);

        useCase.execute(null, "Equipe", Set.of(B));

        verify(notifications).publishPresenceOf("g1");
        assertThat(calledInsideGuard).isFalse();
    }

    @Test
    void addedParticipantPublishesPresenceOutsideTheGuard() {
        AddGroupParticipantsUseCase useCase = addParticipantsUseCase(conversation("g1", ConversationType.GROUP, A, B, C));

        useCase.execute("g1", Set.of(C));

        verify(notifications).publishPresenceOf("g1");
        assertThat(calledInsideGuard).isFalse();
    }

    @Test
    void addingNobodyNewPublishesNothing() {
        AddGroupParticipantsUseCase useCase = addParticipantsUseCase(conversation("g1", ConversationType.GROUP, A, B));

        useCase.execute("g1", Set.of(C));

        verify(notifications, never()).publishPresenceOf(anyString());
    }

    private AddGroupParticipantsUseCase addParticipantsUseCase(Conversation admitted) {
        GroupHelper groups = mock(GroupHelper.class);
        Conversation initial = conversation("g1", ConversationType.GROUP, A, B);
        initial.setVersion(1L);
        given(groups.managedConversation("g1")).willReturn(initial);
        given(admissions.admitParticipants(any(), any(), any(), any())).willReturn(admitted);
        return new AddGroupParticipantsUseCase(admissions, rbac, users, groups, notifications, guard);
    }

    private static Conversation conversation(String id, ConversationType type, UUID... participants) {
        Conversation conversation = new Conversation();
        conversation.setId(id);
        conversation.setConversationType(type);
        conversation.setCreatedBy(A);
        conversation.setParticipantIds(new LinkedHashSet<>(List.of(participants)));
        return conversation;
    }
}
