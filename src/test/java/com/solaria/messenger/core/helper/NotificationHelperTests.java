package com.solaria.messenger.core.helper;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.solaria.messenger.core.model.Message;
import com.solaria.messenger.core.port.ConversationEventPublisher;
import com.solaria.messenger.core.port.MessageEventPublisher;
import com.solaria.messenger.core.repository.ConversationRepository;
import com.solaria.messenger.core.support.ConversationMutationGuard;
import com.solaria.messenger.core.support.ConversationSyncClock;

/**
 * Cobre o fan-out dos eventos {@code MESSAGE_*}: cada participante autorizado da conversa recebe, inclusive o autor,
 * quem perdeu acesso não recebe e uma falha de leitura nunca vira erro do comando.
 */
class NotificationHelperTests {

    private static final String CONVERSATION = "conv-1";
    private static final UUID AUTHOR = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID PEER = UUID.fromString("22222222-2222-2222-2222-222222222222");

    private ConversationAccessHelper access;
    private MessageEventPublisher messageEvents;
    private NotificationHelper notifications;
    private Message message;

    @BeforeEach
    void setUp() {
        access = mock(ConversationAccessHelper.class);
        messageEvents = mock(MessageEventPublisher.class);
        ConversationSyncClock clock = mock(ConversationSyncClock.class);
        notifications = new NotificationHelper(mock(ConversationRepository.class), access,
                mock(ConversationProjectionHelper.class), clock, new ConversationMutationGuard(),
                mock(ConversationEventPublisher.class), messageEvents);
        message = new Message();
        message.setId("msg-1");
        message.setConversationId(CONVERSATION);
        message.setSenderId(AUTHOR);
    }

    @Test
    void createdEventGoesToEveryAuthorizedParticipantIncludingTheAuthor() {
        given(access.authorizedParticipants(Set.of(CONVERSATION))).willReturn(Map.of(CONVERSATION, Set.of(AUTHOR, PEER)));

        notifications.notifyMessageCreated(message);

        verify(messageEvents).messageCreated(message, Set.of(AUTHOR, PEER));
    }

    @Test
    void editedAndDeletedEventsUseTheSameFanOut() {
        given(access.authorizedParticipants(Set.of(CONVERSATION))).willReturn(Map.of(CONVERSATION, Set.of(AUTHOR, PEER)));

        notifications.notifyMessageEdited(message);
        notifications.notifyMessageDeleted(message);

        verify(messageEvents).messageEdited(message, Set.of(AUTHOR, PEER));
        verify(messageEvents).messageDeleted(message, Set.of(AUTHOR, PEER));
    }

    @Test
    void someoneWhoLostAccessIsNotAmongTheRecipients() {
        // a leitura nova da conversa já não tem o PEER: a lista do envio não vale
        given(access.authorizedParticipants(Set.of(CONVERSATION))).willReturn(Map.of(CONVERSATION, Set.of(AUTHOR)));

        notifications.notifyMessageCreated(message);

        verify(messageEvents).messageCreated(message, Set.of(AUTHOR));
    }

    @Test
    void conversationWithoutAuthorizedParticipantsPublishesNothing() {
        given(access.authorizedParticipants(Set.of(CONVERSATION))).willReturn(Map.of());

        notifications.notifyMessageCreated(message);

        verify(messageEvents, never()).messageCreated(any(), any());
    }

    @Test
    void failureReadingParticipantsIsLoggedAndNeverReachesTheCommand() {
        given(access.authorizedParticipants(Set.of(CONVERSATION))).willThrow(new IllegalStateException("mongo fora"));

        assertThatCode(() -> notifications.notifyMessageCreated(message)).doesNotThrowAnyException();

        verify(messageEvents, never()).messageCreated(any(), any());
    }
}
