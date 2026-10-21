package com.solaria.messenger.websocket.security;

import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.springframework.messaging.Message;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.solaria.messenger.websocket.dto.response.WsEventEnvelope;

/** Identifica a conversa a que um destino ou frame STOMP se refere, compartilhado pela entrada e pela saída. */
final class StompDestinations {

    private static final ObjectMapper json = new ObjectMapper();
    private static final String TOPIC = "/topic/conversations/";
    private static final Pattern CONVERSATION_TOPIC_PATTERN =
            Pattern.compile("^/topic/conversations/([A-Za-z0-9_-]+)$");

    private StompDestinations() { }

    /** ID da conversa de um tópico {@code /topic/conversations/{id}} válido, ou {@code null} para outros destinos. */
    static String conversationTopicId(String destination) {
        Matcher matcher = CONVERSATION_TOPIC_PATTERN.matcher(destination == null ? "" : destination);
        return matcher.matches() ? matcher.group(1) : null;
    }

    /**
     * Conversa do frame pelo destino ou pelo {@code conversationId} do corpo; {@code null} quando o frame
     * não pertence a uma conversa (feedback privado em fila do usuário).
     */
    static String conversationId(Message<?> message) {
        String destination = SimpMessageHeaderAccessor.wrap(message).getDestination();
        if (destination != null && destination.startsWith(TOPIC)) return destination.substring(TOPIC.length());
        Object payload = message.getPayload();
        if (payload instanceof WsEventEnvelope envelope) return envelope.getConversationId();
        try {
            var body = payload instanceof byte[] bytes ? json.readTree(bytes)
                    : payload instanceof String text ? json.readTree(text) : null;
            if (body != null && body.path("conversationId").isTextual()) return body.path("conversationId").asText();
        } catch (IOException notAnEnvelope) {
            // Feedback de fila do usuário sem conversa continua permitido.
        }
        return null;
    }
}
