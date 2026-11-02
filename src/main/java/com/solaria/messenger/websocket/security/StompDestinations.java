package com.solaria.messenger.websocket.security;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.messaging.Message;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.solaria.messenger.websocket.dto.response.WsEventEnvelope;

/**
 * Destinos STOMP do broker, compartilhados por quem publica, pela autorização de entrada e pela de saída.
 *
 * <p>
 * O RabbitMQ recusa '/' depois de {@code /topic}, por isso o separador é '.'. Os padrões recusam '.', '*' e '#' no id,
 * senão um cliente assinaria um curinga do RabbitMQ e receberia eventos de outros.
 * </p>
 * <ul>
 *     <li>{@code /topic/users.{userId}.{acks|errors|events}} -> eventos privados do user, em todos os pods. O
 *         {@code events} é o canal único das conversas: {@code MESSAGE_*}, {@code CONVERSATION_UPDATED},
 *         {@code CONVERSATION_REMOVED}, {@code TYPING_*}, {@code USER_ONLINE/OFFLINE} e {@code PRESENCE_SNAPSHOT}.</li>
 * </ul>
 */
public final class StompDestinations {

    private static final ObjectMapper json = new ObjectMapper();
    private static final String USER_TOPIC = "/topic/users.";
    private static final Pattern USER_TOPIC_PATTERN =
            Pattern.compile("^/topic/users\\.([0-9a-f-]{36})\\.(acks|errors|events)$");

    private StompDestinations() { }

    /** Tópico privado do user; {@code kind} é {@code acks}, {@code errors} ou {@code events}. */
    public static String userTopic(UUID userId, String kind) {
        return USER_TOPIC + userId + "." + kind;
    }

    /** Dono de um tópico privado {@code /topic/users.{userId}.{tipo}} válido, ou {@code null} para outros destinos. */
    static String userTopicOwner(String destination) {
        Matcher matcher = USER_TOPIC_PATTERN.matcher(destination == null ? "" : destination);
        return matcher.matches() ? matcher.group(1) : null;
    }

    /**
     * Conversa do frame pelo {@code conversationId} do corpo; {@code null} quando o frame não pertence
     * a uma conversa (feedback privado no tópico do usuário).
     */
    static String conversationId(Message<?> message) {
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
