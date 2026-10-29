package com.solaria.messenger.websocket.publisher;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import com.solaria.messenger.core.contract.response.ConversationListItemResponseDTO;
import com.solaria.messenger.core.contract.response.MessageResponseDTO;
import com.solaria.messenger.core.model.Message;
import com.solaria.messenger.websocket.dto.response.WsEventEnvelope;
import com.solaria.messenger.websocket.security.StompDestinations;

/**
 * Cobre o destino dos eventos duráveis: {@code MESSAGE_*}, {@code CONVERSATION_UPDATED} e {@code CONVERSATION_REMOVED}
 * vão ao tópico {@code /topic/users.{id}.events} de cada destinatário, e nada sai em {@code /topic/conversations.{id}}
 * nem em {@code .conversations}.
 */
class StompEventPublishersTests {

    private static final String CONVERSATION = "conv-1";
    private static final UUID AUTHOR = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID PEER = UUID.fromString("22222222-2222-2222-2222-222222222222");

    private SimpMessagingTemplate messaging;
    private StompMessageEventPublisher messagePublisher;
    private StompConversationEventPublisher conversationPublisher;

    @BeforeEach
    void setUp() {
        messaging = mock(SimpMessagingTemplate.class);
        messagePublisher = new StompMessageEventPublisher(messaging);
        conversationPublisher = new StompConversationEventPublisher(messaging);
    }

    @Test
    void messageCreatedGoesToTheEventsTopicOfEachRecipientWithTheMessageIdAsEventId() {
        messagePublisher.messageCreated(message(), Set.of(AUTHOR, PEER));

        ArgumentCaptor<Object> frames = ArgumentCaptor.forClass(Object.class);
        verify(messaging).convertAndSend(eq(StompDestinations.userTopic(AUTHOR, "events")), frames.capture());
        verify(messaging).convertAndSend(eq(StompDestinations.userTopic(PEER, "events")), frames.capture());
        for (Object frame : frames.getAllValues()) {
            WsEventEnvelope envelope = (WsEventEnvelope) frame;
            assertThat(envelope.getType()).isEqualTo("MESSAGE_CREATED");
            assertThat(envelope.getConversationId()).isEqualTo(CONVERSATION);
            assertThat(envelope.getEventId()).isEqualTo("msg-1");
            MessageResponseDTO payload = (MessageResponseDTO) envelope.getPayload();
            assertThat(payload.getId()).isEqualTo("msg-1");
            assertThat(payload.getChangeVersion()).isEqualTo(42L);
        }
    }

    @Test
    void messageEventsLeaveTheConversationTopicAndTheOldUserTopicUntouched() {
        messagePublisher.messageCreated(message(), Set.of(AUTHOR));
        messagePublisher.messageEdited(message(), Set.of(AUTHOR));
        messagePublisher.messageDeleted(message(), Set.of(AUTHOR));

        verify(messaging, never()).convertAndSend(startsWith("/topic/conversations."), any(Object.class));
        verify(messaging, never()).convertAndSend(contains(".conversations"), any(Object.class));
        verify(messaging, times(3)).convertAndSend(eq(StompDestinations.userTopic(AUTHOR, "events")),
                any(Object.class));
    }

    @Test
    void editedAndDeletedShareOneFreshEventIdAcrossRecipients() {
        messagePublisher.messageEdited(message(), new LinkedHashSet<>(List.of(AUTHOR, PEER)));
        messagePublisher.messageDeleted(message(), new LinkedHashSet<>(List.of(AUTHOR, PEER)));

        ArgumentCaptor<Object> frames = ArgumentCaptor.forClass(Object.class);
        verify(messaging, times(4)).convertAndSend(anyString(), frames.capture());
        List<WsEventEnvelope> sent = frames.getAllValues().stream().map(WsEventEnvelope.class::cast).toList();
        assertThat(sent).extracting(WsEventEnvelope::getType)
                .containsExactly("MESSAGE_EDITED", "MESSAGE_EDITED", "MESSAGE_DELETED", "MESSAGE_DELETED");
        assertThat(sent.get(0).getEventId()).isEqualTo(sent.get(1).getEventId()).isNotEqualTo("msg-1");
        assertThat(sent.get(2).getEventId()).isEqualTo(sent.get(3).getEventId()).isNotEqualTo(sent.get(0).getEventId());
    }

    @Test
    void failureWithOneRecipientDoesNotStopTheOthers() {
        doThrow(new IllegalStateException("broker fora")).when(messaging)
                .convertAndSend(eq(StompDestinations.userTopic(AUTHOR, "events")), any(Object.class));

        messagePublisher.messageCreated(message(), new LinkedHashSet<>(List.of(AUTHOR, PEER)));

        verify(messaging).convertAndSend(eq(StompDestinations.userTopic(PEER, "events")), any(Object.class));
    }

    @Test
    void conversationUpdatedAndRemovedGoToTheEventsTopic() {
        ConversationListItemResponseDTO item = new ConversationListItemResponseDTO(CONVERSATION, "epoch-1", 9L, null,
                Set.of(AUTHOR, PEER), null, null, null, null, null, null, null, null, null, null, null, null, null, null);

        conversationPublisher.conversationUpdated(PEER, item);
        conversationPublisher.conversationRemoved(PEER, CONVERSATION, "epoch-1", 9L);

        ArgumentCaptor<Object> frames = ArgumentCaptor.forClass(Object.class);
        verify(messaging, times(2)).convertAndSend(eq(StompDestinations.userTopic(PEER, "events")), frames.capture());
        verify(messaging, never()).convertAndSend(contains(".conversations"), any(Object.class));
        assertThat(frames.getAllValues()).extracting(frame -> ((WsEventEnvelope) frame).getType())
                .containsExactly("CONVERSATION_UPDATED", "CONVERSATION_REMOVED");
        assertThat(((WsEventEnvelope) frames.getAllValues().get(1)).getPayload())
                .isEqualTo(Map.of("id", CONVERSATION, "syncEpoch", "epoch-1", "syncVersion", 9L));
    }

    private static Message message() {
        Message message = new Message();
        message.setId("msg-1");
        message.setConversationId(CONVERSATION);
        message.setSenderId(AUTHOR);
        message.setContent("oi");
        message.setTimestamp(Instant.parse("2026-10-28T10:00:00Z"));
        message.setChangeVersion(42L);
        return message;
    }
}
