package com.solaria.messenger.websocket.security;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.messaging.Message;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.solaria.messenger.websocket.dto.response.WsEventEnvelope;

/**
 * Destinos STOMP do broker, compartilhados por quem publica, pela autorização de entrada e pela de saída.
 *
 * <p>
 * O RabbitMQ recusa '/' depois de {@code /topic}, por isso o separador é '.'. Os padrões recusam '.', '*' e '#'
 * no id, senão um cliente assinaria um curinga do RabbitMQ e receberia eventos de outros.
 * </p>
 * <ul>
 *     <li>{@code /topic/conversations.{id}} -> eventos de uma conversa.</li>
 *     <li>{@code /topic/users.{userId}.{acks|errors|conversations}} -> eventos privados do user, em todos os pods.</li>
 * </ul>
 */
public final class StompDestinations {

    private static final ObjectMapper json = new ObjectMapper();
    private static final String CONVERSATION_TOPIC = "/topic/conversations.";
    private static final String USER_TOPIC = "/topic/users.";
    private static final Pattern CONVERSATION_TOPIC_PATTERN =
            Pattern.compile("^/topic/conversations\\.([A-Za-z0-9_-]+)$");
    private static final Pattern USER_TOPIC_PATTERN =
            Pattern.compile("^/topic/users\\.([0-9a-f-]{36})\\.(acks|errors|conversations)$");

    private StompDestinations() { }

    /** Tópico de uma conversa. */
    public static String conversationTopic(String conversationId) {
        return CONVERSATION_TOPIC + conversationId;
    }

    /** Tópico privado do user; {@code kind} é {@code acks}, {@code errors} ou {@code conversations}. */
    public static String userTopic(UUID userId, String kind) {
        return USER_TOPIC + userId + "." + kind;
    }

    /** ID da conversa de um tópico {@code /topic/conversations.{id}} válido, ou {@code null} para outros destinos. */
    static String conversationTopicId(String destination) {
        Matcher matcher = CONVERSATION_TOPIC_PATTERN.matcher(destination == null ? "" : destination);
        return matcher.matches() ? matcher.group(1) : null;
    }

    /** Dono de um tópico privado {@code /topic/users.{userId}.{tipo}} válido, ou {@code null} para outros destinos. */
    static String userTopicOwner(String destination) {
        Matcher matcher = USER_TOPIC_PATTERN.matcher(destination == null ? "" : destination);
        return matcher.matches() ? matcher.group(1) : null;
    }

    /**
     * Conversa do frame pelo destino ou pelo {@code conversationId} do corpo; {@code null} quando o frame
     * não pertence a uma conversa (feedback privado no tópico do usuário).
     */
    static String conversationId(Message<?> message) {
        String destination = SimpMessageHeaderAccessor.wrap(message).getDestination();
        if (destination != null && destination.startsWith(CONVERSATION_TOPIC)) return destination.substring(CONVERSATION_TOPIC.length());
        Object payload = message.getPayload();
        if (payload instanceof WsEventEnvelope envelope) return envelope.getConversationId();
        try {
            JsonNode body = null;
            if (payload instanceof byte[] bytes) body = json.readTree(bytes);
            else if (payload instanceof String text) body = json.readTree(text);
            if (body != null && body.path("conversationId").isTextual()) return body.path("conversationId").asText();
        } catch (IOException notAnEnvelope) {
            // Feedback do tópico do usuário sem conversa continua permitido.
        }
        return null;
    }
}
