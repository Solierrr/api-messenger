package com.solaria.messenger.websocket.presence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.MessageHeaders;
import org.springframework.messaging.converter.MessageConverter;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.support.MessageBuilder;

import com.solaria.messenger.core.helper.ConversationAccessHelper;
import com.solaria.messenger.websocket.dto.response.PresenceSnapshotDTO;
import com.solaria.messenger.websocket.dto.response.WsEventEnvelope;
import com.solaria.messenger.websocket.event.SessionPresenceChanged;
import com.solaria.messenger.websocket.event.UserEventsSubscribed;
import com.solaria.messenger.websocket.security.StompDestinations;
import com.solaria.messenger.websocket.session.LocalWebSocketSessionRegistry;

/**
 * Cobre a presença no canal único do usuário: o retrato na assinatura, o aviso de online/offline uma vez por contato e
 * o retrato parcial quando uma conversa ganha gente.
 */
class PresenceServiceTests {

    private static final UUID A = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID B = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private static final UUID C = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");

    private ConversationAccessHelper access;
    private LocalWebSocketSessionRegistry sessions;
    private SimpMessagingTemplate messaging;
    private MessageChannel outbound;
    private MessageConverter converter;
    private PresenceService presence;

    @BeforeEach
    void setUp() {
        access = mock(ConversationAccessHelper.class);
        sessions = mock(LocalWebSocketSessionRegistry.class);
        messaging = mock(SimpMessagingTemplate.class);
        outbound = mock(MessageChannel.class);
        converter = mock(MessageConverter.class);
        presence = new PresenceService(access, sessions, messaging, outbound, converter);
    }

    @Test
    void snapshotOnEventsSubscriptionCoversAllContactsWithoutSelf() {
        givenOperationalSession("s1", A);
        given(access.contactsOf(A)).willReturn(Set.of(B, C));
        given(sessions.presenceSnapshot("sub-1", Set.of(B, C)))
                .willReturn(new PresenceSnapshotDTO("sub-1", "e1", 7, Map.of(B, true, C, false)));
        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        ArgumentCaptor<MessageHeaders> headers = ArgumentCaptor.forClass(MessageHeaders.class);
        given(converter.toMessage(payload.capture(), headers.capture())).willAnswer(call -> MessageBuilder.withPayload("x").build());
        given(outbound.send(any())).willReturn(true);

        presence.onUserEventsSubscribed(new UserEventsSubscribed("s1", "sub-1", A));

        WsEventEnvelope envelope = (WsEventEnvelope) payload.getValue();
        assertThat(envelope.getType()).isEqualTo("PRESENCE_SNAPSHOT");
        assertThat(envelope.getConversationId()).isNull();
        assertThat(((PresenceSnapshotDTO) envelope.getPayload()).users()).containsOnlyKeys(B, C);
        assertThat(SimpMessageHeaderAccessor.getDestination(headers.getValue()))
                .isEqualTo(StompDestinations.userTopic(A, "events"));
        assertThat(SimpMessageHeaderAccessor.getSubscriptionId(headers.getValue())).isEqualTo("sub-1");
    }

    @Test
    void snapshotForUserWithoutContactsIsEmpty() {
        givenOperationalSession("s1", A);
        given(access.contactsOf(A)).willReturn(Set.of());
        given(sessions.presenceSnapshot("sub-1", Set.of()))
                .willReturn(new PresenceSnapshotDTO("sub-1", "e1", 7, Map.of()));
        ArgumentCaptor<Object> payload = ArgumentCaptor.forClass(Object.class);
        given(converter.toMessage(payload.capture(), any(MessageHeaders.class))).willAnswer(call -> MessageBuilder.withPayload("x").build());
        given(outbound.send(any())).willReturn(true);

        presence.onUserEventsSubscribed(new UserEventsSubscribed("s1", "sub-1", A));

        assertThat(((PresenceSnapshotDTO) ((WsEventEnvelope) payload.getValue()).getPayload()).users()).isEmpty();
    }

    @Test
    void subscriptionFromRevokedSessionGetsNoSnapshot() {
        given(sessions.isOperational("s1")).willReturn(false);

        presence.onUserEventsSubscribed(new UserEventsSubscribed("s1", "sub-1", A));

        verify(outbound, never()).send(any());
    }

    @Test
    void presenceChangeGoesOnceToEachContactAndNeverToSelf() {
        given(sessions.isOnlineAnywhere(A)).willReturn(true);
        // B divide duas conversas com A, mas contactsOf já devolve cada contato uma vez
        given(access.contactsOf(A)).willReturn(Set.of(B, C));

        presence.onPresenceChanged(new SessionPresenceChanged(A, true, "e1", 9));

        ArgumentCaptor<Object> frames = ArgumentCaptor.forClass(Object.class);
        verify(messaging).convertAndSend(eq(StompDestinations.userTopic(B, "events")), frames.capture());
        verify(messaging).convertAndSend(eq(StompDestinations.userTopic(C, "events")), any(Object.class));
        verify(messaging, never()).convertAndSend(eq(StompDestinations.userTopic(A, "events")), any(Object.class));
        verify(messaging, never()).convertAndSend(startsWith("/topic/conversations."), any(Object.class));
        WsEventEnvelope envelope = (WsEventEnvelope) frames.getValue();
        assertThat(envelope.getType()).isEqualTo("USER_ONLINE");
        assertThat(envelope.getConversationId()).isNull();
        assertThat(payloadOf(envelope)).containsEntry("userId", A.toString())
                .containsEntry("presenceVersion", 9L);
    }

    @Test
    void staleOfflineEventIsIgnoredWhenTheUserIsOnlineAgain() {
        given(sessions.isOnlineAnywhere(A)).willReturn(true);

        presence.onPresenceChanged(new SessionPresenceChanged(A, false, "e1", 9));

        verify(messaging, never()).convertAndSend(anyString(), any(Object.class));
    }

    @Test
    void presenceFailureWithOneContactDoesNotStopTheOthers() {
        given(sessions.isOnlineAnywhere(A)).willReturn(true);
        given(access.contactsOf(A)).willReturn(new LinkedHashSet<>(List.of(B, C)));
        doThrow(new IllegalStateException("broker")).when(messaging)
                .convertAndSend(eq(StompDestinations.userTopic(B, "events")), any(Object.class));

        presence.onPresenceChanged(new SessionPresenceChanged(A, true, "e1", 9));

        verify(messaging).convertAndSend(eq(StompDestinations.userTopic(C, "events")), any(Object.class));
    }

    @Test
    void conversationJoinedSendsEachParticipantTheOthers() {
        Set<UUID> participants = new LinkedHashSet<>(List.of(A, B, C));
        given(access.authorizedParticipants(Set.of("c1"))).willReturn(Map.of("c1", participants));
        given(sessions.presenceSnapshot(null, participants))
                .willReturn(new PresenceSnapshotDTO(null, "e1", 7, Map.of(A, true, B, false, C, true)));

        presence.conversationJoined("c1");

        assertThat(snapshotSentTo(A).users()).containsOnlyKeys(B, C);
        assertThat(snapshotSentTo(B).users()).containsOnlyKeys(A, C);
        assertThat(snapshotSentTo(C).users()).containsOnlyKeys(A, B);
        assertThat(snapshotSentTo(B).subscriptionId()).isNull();
        assertThat(snapshotSentTo(B).users()).containsEntry(A, true).containsEntry(C, true);
    }

    @Test
    void conversationJoinedWithoutAuthorizedParticipantsSendsNothing() {
        given(access.authorizedParticipants(Set.of("gone"))).willReturn(Map.of());

        presence.conversationJoined("gone");

        verify(messaging, never()).convertAndSend(anyString(), any(Object.class));
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Object> payloadOf(WsEventEnvelope envelope) {
        return (Map<String, Object>) envelope.getPayload();
    }

    private PresenceSnapshotDTO snapshotSentTo(UUID recipient) {
        ArgumentCaptor<Object> frame = ArgumentCaptor.forClass(Object.class);
        verify(messaging).convertAndSend(eq(StompDestinations.userTopic(recipient, "events")), frame.capture());
        WsEventEnvelope envelope = (WsEventEnvelope) frame.getValue();
        assertThat(envelope.getType()).isEqualTo("PRESENCE_SNAPSHOT");
        assertThat(envelope.getConversationId()).isNull();
        return (PresenceSnapshotDTO) envelope.getPayload();
    }

    private void givenOperationalSession(String sessionId, UUID user) {
        given(sessions.isOperational(sessionId)).willReturn(true);
        given(sessions.userId(sessionId)).willReturn(user);
    }
}
