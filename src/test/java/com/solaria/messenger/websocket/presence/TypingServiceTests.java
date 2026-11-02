package com.solaria.messenger.websocket.presence;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import com.solaria.messenger.core.helper.ConversationAccessHelper;
import com.solaria.messenger.core.helper.LookupHelper;
import com.solaria.messenger.core.model.Conversation;
import com.solaria.messenger.websocket.dto.response.WsEventEnvelope;
import com.solaria.messenger.websocket.event.SessionPresenceChanged;
import com.solaria.messenger.websocket.security.StompDestinations;
import com.solaria.messenger.websocket.session.LocalWebSocketSessionRegistry;

/**
 * Cobre o "digitando" no canal único: vai ao {@code events} de cada participante autorizado, menos de quem digita, e o
 * fim do typing pela queda da sessão não falha quando a conversa sumiu.
 */
class TypingServiceTests {

    private static final String CONVERSATION = "c1";
    private static final UUID A = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID B = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private static final UUID C = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");

    private ConversationAccessHelper access;
    private LookupHelper lookup;
    private TypingPresenceService typingPresence;
    private LocalWebSocketSessionRegistry sessions;
    private SimpMessagingTemplate messaging;
    private TypingService typing;

    @BeforeEach
    void setUp() {
        access = mock(ConversationAccessHelper.class);
        lookup = mock(LookupHelper.class);
        typingPresence = new TypingPresenceService();
        sessions = mock(LocalWebSocketSessionRegistry.class);
        messaging = mock(SimpMessagingTemplate.class);
        typing = new TypingService(access, lookup, typingPresence, sessions, messaging);
        Conversation conversation = new Conversation();
        conversation.setId(CONVERSATION);
        given(lookup.requireConversation(CONVERSATION)).willReturn(conversation);
    }

    @Test
    void typingGoesToEveryAuthorizedParticipantButTheTypist() {
        given(access.authorizedParticipants(Set.of(CONVERSATION))).willReturn(Map.of(CONVERSATION, Set.of(A, B, C)));

        typing.start(CONVERSATION, A);

        ArgumentCaptor<Object> frames = ArgumentCaptor.forClass(Object.class);
        verify(messaging).convertAndSend(eq(StompDestinations.userTopic(B, "events")), frames.capture());
        verify(messaging).convertAndSend(eq(StompDestinations.userTopic(C, "events")), any(Object.class));
        verify(messaging, never()).convertAndSend(eq(StompDestinations.userTopic(A, "events")), any(Object.class));
        verify(messaging, never()).convertAndSend(startsWith("/topic/conversations."), any(Object.class));
        WsEventEnvelope envelope = (WsEventEnvelope) frames.getValue();
        assertThat(envelope.getType()).isEqualTo("TYPING_STARTED");
        assertThat(envelope.getConversationId()).isEqualTo(CONVERSATION);
        assertThat(envelope.getPayload()).isEqualTo(Map.of("userId", A.toString()));
    }

    @Test
    void stopGoesToTheSameRecipientsWithTheStoppedType() {
        given(access.authorizedParticipants(Set.of(CONVERSATION))).willReturn(Map.of(CONVERSATION, Set.of(A, B)));

        typing.stop(CONVERSATION, A);

        ArgumentCaptor<Object> frames = ArgumentCaptor.forClass(Object.class);
        verify(messaging).convertAndSend(eq(StompDestinations.userTopic(B, "events")), frames.capture());
        assertThat(((WsEventEnvelope) frames.getValue()).getType()).isEqualTo("TYPING_STOPPED");
    }

    @Test
    void stopOnDisconnectWithDeletedConversationSendsNothing() {
        typingPresence.start(CONVERSATION, A.toString());
        given(sessions.isOnline(A)).willReturn(false);
        given(access.authorizedParticipants(Set.of(CONVERSATION))).willReturn(Map.of());

        assertThatCode(() -> typing.onPresenceChanged(new SessionPresenceChanged(A, false, "e1", 3)))
                .doesNotThrowAnyException();

        verify(messaging, never()).convertAndSend(anyString(), any(Object.class));
    }

    @Test
    void stopOnDisconnectReachesTheOtherParticipants() {
        typingPresence.start(CONVERSATION, A.toString());
        given(sessions.isOnline(A)).willReturn(false);
        given(access.authorizedParticipants(Set.of(CONVERSATION))).willReturn(Map.of(CONVERSATION, Set.of(A, B)));

        typing.onPresenceChanged(new SessionPresenceChanged(A, false, "e1", 3));

        verify(messaging).convertAndSend(eq(StompDestinations.userTopic(B, "events")), any(Object.class));
    }

    @Test
    void failureWithOneRecipientDoesNotStopTheOthers() {
        given(access.authorizedParticipants(Set.of(CONVERSATION)))
                .willReturn(Map.of(CONVERSATION, new java.util.LinkedHashSet<>(java.util.List.of(A, B, C))));
        doThrow(new IllegalStateException("broker")).when(messaging)
                .convertAndSend(eq(StompDestinations.userTopic(B, "events")), any(Object.class));

        assertThatCode(() -> typing.start(CONVERSATION, A)).doesNotThrowAnyException();

        verify(messaging).convertAndSend(eq(StompDestinations.userTopic(C, "events")), any(Object.class));
    }

    @Test
    void typingReachesMutedConversationsBecauseTheServiceNeverReadsPersonalState() {
        // o estado pessoal (silenciado, arquivado) não entra no fan-out: B recebe como qualquer outro participante
        given(access.authorizedParticipants(Set.of(CONVERSATION))).willReturn(Map.of(CONVERSATION, Set.of(A, B)));

        typing.start(CONVERSATION, A);

        verify(messaging).convertAndSend(eq(StompDestinations.userTopic(B, "events")), any(Object.class));
    }
}
