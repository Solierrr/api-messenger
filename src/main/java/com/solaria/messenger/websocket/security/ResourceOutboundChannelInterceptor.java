package com.solaria.messenger.websocket.security;

import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;

import com.solaria.messenger.core.exception.BusinessException;
import com.solaria.messenger.core.service.ConversationAuthorizationService;
import com.solaria.messenger.websocket.session.LocalWebSocketSessionRegistry;

/** Verifica novas saídas pelo estado atual, sem interceptar escrita física ou drenar buffers. */
public class ResourceOutboundChannelInterceptor implements ChannelInterceptor {

    private final LocalWebSocketSessionRegistry sessions;
    private final ConversationAuthorizationService authorization;

    public ResourceOutboundChannelInterceptor(LocalWebSocketSessionRegistry sessions,
            ConversationAuthorizationService authorization) {
        this.sessions = sessions;
        this.authorization = authorization;
    }

    /** Frames já entregues ao transporte podem concluir; novas publicações exigem sessão e acesso válidos. */
    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        var headers = SimpMessageHeaderAccessor.wrap(message);
        if (headers.getMessageType() != SimpMessageType.MESSAGE) return message;
        String sessionId = headers.getSessionId();
        if (!sessions.isOperational(sessionId)) return null;
        String conversationId = StompDestinations.conversationId(message);
        if (conversationId != null) {
            try {
                authorization.requireParticipant(conversationId, sessions.userId(sessionId));
            } catch (BusinessException denied) {
                return null;
            }
        }
        return message;
    }
}
