package com.solaria.messenger.websocket.security;

import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

import com.solaria.messenger.websocket.dto.response.WsErrorPayload;
import com.solaria.messenger.core.exception.BusinessException;
import com.solaria.messenger.core.helper.ConversationAccessHelper;
import com.solaria.messenger.websocket.event.StompFrameDenied;

/**
 * Autoriza destinos STOMP de um frame já autenticado: allowlist de SUBSCRIBE/SEND e participação atual
 * na conversa. Uma recusa descarta só o frame e avisa o usuário no tópico {@code /topic/users.{userId}.errors}.
 */
@Component
public class StompDestinationAuthorizer {

    private static final Logger log = LoggerFactory.getLogger(StompDestinationAuthorizer.class);

    /** Únicos destinos {@code /app/**} que 
     * {@link com.solaria.messenger.websocket.controller.MessageWebSocketController} mapeia.
     * 
     */
    private static final Set<String> ALLOWED_SEND_DESTINATIONS = Set.of(
            "/app/messages.send", "/app/typing.started", "/app/typing.stopped", "/app/presence.ping");

    /** Headers de SUBSCRIBE com que o RabbitMQ declara a fila (os {@code x-*} também são removidos). */
    private static final Set<String> QUEUE_OPTION_HEADERS = Set.of("durable", "persistent", "auto-delete", "exclusive");

    private final ConversationAccessHelper authorization;
    private final ApplicationEventPublisher events;

    public StompDestinationAuthorizer(ConversationAccessHelper authorization, ApplicationEventPublisher events) {
        this.authorization = authorization;
        this.events = events;
    }

    /** Libera o tópico privado do próprio usuário e tópicos de conversa em que ele participa. */
    public <T> Message<T> authorizeSubscribe(Message<T> message, StompHeaderAccessor accessor, String userId) {
        String destination = accessor.getDestination();
        if (destination == null) {
            return deny(userId, "SUBSCRIBE_DESTINATION_REQUIRED", "SUBSCRIBE sem destino.");
        }

        // tópico privado: só o dono assina
        String owner = StompDestinations.userTopicOwner(destination);
        if (owner != null && owner.equals(userId)) {
            return withoutQueueOptions(message);
        }

        String conversationId = StompDestinations.conversationTopicId(destination);
        if (conversationId == null) {
            log.warn("SUBSCRIBE recusado (userId={}, destino={}): fora da allowlist", userId, destination);
            return deny(userId, "SUBSCRIBE_NOT_ALLOWED", "Destino de subscription nao permitido.");
        }

        try {
            authorization.requireParticipant(conversationId, UUID.fromString(userId));
            return withoutQueueOptions(message);
        } catch (BusinessException denied) {
            return deny(userId, denied.getErrorCode(), denied.getMessage());
        }
    }

    /**
     * Tira do SUBSCRIBE os headers que o RabbitMQ usa para declarar a fila; sem eles a fila é exclusiva e some
     * com a sessão. Com eles o cliente poderia pedir uma fila durável que acumula eventos depois da desconexão.
     */
    private static <T> Message<T> withoutQueueOptions(Message<T> message) {
        StompHeaderAccessor headers = StompHeaderAccessor.wrap(message);
        headers.toNativeHeaderMap().keySet().stream()
                .filter(name -> QUEUE_OPTION_HEADERS.contains(name) || name.startsWith("x-"))
                .forEach(headers::removeNativeHeader);
        return MessageBuilder.createMessage(message.getPayload(), headers.getMessageHeaders());
    }

    /** Aceita SEND só para os destinos /app mapeados e exige participação na conversa do frame. */
    public <T> Message<T> authorizeSend(Message<T> message, StompHeaderAccessor accessor, String userId) {
        String destination = accessor.getDestination();
        if (destination == null || !ALLOWED_SEND_DESTINATIONS.contains(destination)) {
            log.warn("SEND recusado (userId={}, destino={}): fora da allowlist", userId, destination);
            return deny(userId, "SEND_NOT_ALLOWED", "Destino de SEND nao permitido.");
        }
        return authorizeResource(message, userId);
    }

    /** Frames sem conversa identificável passam; os demais exigem participação atual. */
    public <T> Message<T> authorizeResource(Message<T> message, String userId) {
        String conversationId = StompDestinations.conversationId(message);
        if (conversationId == null) return message;
        try {
            authorization.requireParticipant(conversationId, UUID.fromString(userId));
            return message;
        } catch (BusinessException denied) {
            return deny(userId, denied.getErrorCode(), denied.getMessage());
        }
    }

    /** Avisa o cliente no tópico privado de erros e descarta o frame (sem fechar a conexão). */
    public <T> Message<T> deny(String userId, String code, String detail) {
        WsErrorPayload payload = WsErrorPayload.builder()
                .code(code)
                .message(detail)
                .build();
        events.publishEvent(new StompFrameDenied(UUID.fromString(userId), payload));
        return null;
    }
}
