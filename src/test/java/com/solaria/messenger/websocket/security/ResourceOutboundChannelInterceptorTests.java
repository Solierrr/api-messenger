package com.solaria.messenger.websocket.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;

import com.solaria.messenger.core.exception.UnauthorizedAccessException;
import com.solaria.messenger.core.helper.ConversationAccessHelper;
import com.solaria.messenger.websocket.session.LocalWebSocketSessionRegistry;

/**
 * Cobre o filtro de saída no tópico {@code /topic/users.{id}.events}: um {@code MESSAGE_*} de conversa que o usuário
 * perdeu é descartado, o de conversa em que ele participa passa, e o aviso de remoção, que não traz conversa no
 * envelope, passa sem consulta.
 */
class ResourceOutboundChannelInterceptorTests {

    private static final String SESSION = "session-1";
    private static final UUID USER = UUID.fromString("7f8e9a10-1b2c-4d3e-8f40-5a6b7c8d9e0f");

    private LocalWebSocketSessionRegistry sessions;
    private ConversationAccessHelper authorization;
    private ResourceOutboundChannelInterceptor interceptor;
    private final MessageChannel channel = mock(MessageChannel.class);

    @BeforeEach
    void setUp() {
        sessions = mock(LocalWebSocketSessionRegistry.class);
        authorization = mock(ConversationAccessHelper.class);
        interceptor = new ResourceOutboundChannelInterceptor(sessions, authorization);
        given(sessions.isOperational(SESSION)).willReturn(true);
        given(sessions.userId(SESSION)).willReturn(USER);
    }

    @Test
    void messageEventOfALostConversationIsDroppedOnTheEventsTopic() {
        doThrow(new UnauthorizedAccessException("O usuário não participa desta conversa."))
                .when(authorization).requireParticipant("conv-lost", USER);

        assertThat(interceptor.preSend(eventsFrame("MESSAGE_EDITED", "conv-lost"), channel)).isNull();
    }

    @Test
    void messageEventOfAParticipatingConversationPassesOnTheEventsTopic() {
        Message<byte[]> frame = eventsFrame("MESSAGE_CREATED", "conv-1");

        assertThat(interceptor.preSend(frame, channel)).isSameAs(frame);
        verify(authorization).requireParticipant("conv-1", USER);
    }

    @Test
    void removalNoticeWithoutConversationInTheEnvelopePassesWithoutLookup() {
        Message<byte[]> frame = eventsFrame("CONVERSATION_REMOVED", null);

        assertThat(interceptor.preSend(frame, channel)).isSameAs(frame);
        verifyNoInteractions(authorization);
    }

    @Test
    void frameOfAnInactiveSessionIsDropped() {
        given(sessions.isOperational("session-2")).willReturn(false);
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.MESSAGE);
        accessor.setSessionId("session-2");
        accessor.setDestination(StompDestinations.userTopic(USER, "events"));

        assertThat(interceptor.preSend(
                MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders()), channel)).isNull();
        verifyNoInteractions(authorization);
    }

    private static Message<byte[]> eventsFrame(String type, String conversationId) {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.MESSAGE);
        accessor.setSessionId(SESSION);
        accessor.setDestination(StompDestinations.userTopic(USER, "events"));
        String conversation = conversationId == null ? "" : ",\"conversationId\":\"" + conversationId + "\"";
        byte[] body = ("{\"type\":\"" + type + "\"" + conversation + ",\"eventId\":\"e-1\"}")
                .getBytes(StandardCharsets.UTF_8);
        return MessageBuilder.createMessage(body, accessor.getMessageHeaders());
    }
}
