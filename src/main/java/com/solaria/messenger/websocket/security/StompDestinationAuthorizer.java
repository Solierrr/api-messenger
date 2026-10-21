package com.solaria.messenger.websocket.security;

import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;

import com.solaria.messenger.websocket.dto.response.WsErrorPayload;
import com.solaria.messenger.core.exception.BusinessException;
import com.solaria.messenger.core.service.ConversationAuthorizationService;
import com.solaria.messenger.websocket.event.StompFrameDenied;

/**
 * Autoriza destinos STOMP de um frame já autenticado: allowlist de SUBSCRIBE/SEND e participação atual
 * na conversa. Uma recusa descarta só o frame e avisa o usuário por {@code /user/queue/errors}.
 */
@Component
public class StompDestinationAuthorizer {

    private static final Logger log = LoggerFactory.getLogger(StompDestinationAuthorizer.class);

    /** Eventos de retorno para o próprio user não precisam de autorização de conversa. */
    private static final Set<String> ALLOWED_USER_QUEUE_DESTINATIONS =
            Set.of("/user/queue/acks", "/user/queue/errors", "/user/queue/conversations");

    /** Únicos destinos {@code /app/**} que 
     * {@link com.solaria.messenger.websocket.controller.MessageWebSocketController} mapeia.
     * 
     */
    private static final Set<String> ALLOWED_SEND_DESTINATIONS = Set.of(
            "/app/messages.send", "/app/typing.started", "/app/typing.stopped", "/app/presence.ping");

    private final ConversationAuthorizationService authorization;
    private final ApplicationEventPublisher events;

    public StompDestinationAuthorizer(ConversationAuthorizationService authorization, ApplicationEventPublisher events) {
        this.authorization = authorization;
        this.events = events;
    }

    /** Libera filas próprias do usuário e tópicos de conversa em que ele participa. */
    public Message<?> authorizeSubscribe(Message<?> message, StompHeaderAccessor accessor, String userId) {
        String destination = accessor.getDestination();
        if (destination == null) {
            return deny(userId, "SUBSCRIBE_DESTINATION_REQUIRED", "SUBSCRIBE sem destino.");
        }

        if (ALLOWED_USER_QUEUE_DESTINATIONS.contains(destination)) {
            return message;
        }

        String conversationId = StompDestinations.conversationTopicId(destination);
        if (conversationId == null) {
            log.warn("SUBSCRIBE recusado (userId={}, destino={}): fora da allowlist", userId, destination);
            return deny(userId, "SUBSCRIBE_NOT_ALLOWED", "Destino de subscription nao permitido.");
        }

        try {
            authorization.requireParticipant(conversationId, UUID.fromString(userId));
            return message;
        } catch (BusinessException denied) {
            return deny(userId, denied.getErrorCode(), denied.getMessage());
        }
    }

    /** Aceita SEND só para os destinos /app mapeados e exige participação na conversa do frame. */
    public Message<?> authorizeSend(Message<?> message, StompHeaderAccessor accessor, String userId) {
        String destination = accessor.getDestination();
        if (destination == null || !ALLOWED_SEND_DESTINATIONS.contains(destination)) {
            log.warn("SEND recusado (userId={}, destino={}): fora da allowlist", userId, destination);
            return deny(userId, "SEND_NOT_ALLOWED", "Destino de SEND nao permitido.");
        }
        return authorizeResource(message, userId);
    }

    /** Frames sem conversa identificável passam; os demais exigem participação atual. */
    public Message<?> authorizeResource(Message<?> message, String userId) {
        String conversationId = StompDestinations.conversationId(message);
        if (conversationId == null) return message;
        try {
            authorization.requireParticipant(conversationId, UUID.fromString(userId));
            return message;
        } catch (BusinessException denied) {
            return deny(userId, denied.getErrorCode(), denied.getMessage());
        }
    }

    /** Avisa o cliente por {@code /user/queue/errors} e descarta o frame (sem fechar a conexão). */
    public Message<?> deny(String userId, String code, String detail) {
        WsErrorPayload payload = WsErrorPayload.builder()
                .code(code)
                .message(detail)
                .build();
        events.publishEvent(new StompFrameDenied(UUID.fromString(userId), payload));
        return null;
    }
}
