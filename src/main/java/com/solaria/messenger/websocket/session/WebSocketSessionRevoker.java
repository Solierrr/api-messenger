package com.solaria.messenger.websocket.session;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import com.solaria.messenger.core.port.SessionRevoker;

/**
 * Encerra as conexões do user em todos os pods depois que o domínio registra a perda de participação.
 * Fecha as deste pod na hora e avisa os outros pelo canal Redis {@code messenger:revoke}; sem Redis, nos outros pods
 * o filtro de saída ainda descarta os frames das conversas perdidas.
 */
@Component
public class WebSocketSessionRevoker implements SessionRevoker, MessageListener {

    static final String CHANNEL = "messenger:revoke";
    private static final Logger log = LoggerFactory.getLogger(WebSocketSessionRevoker.class);

    private final LocalWebSocketSessionRegistry sessions;
    private final StringRedisTemplate redis;
    // identifica as revogações deste pod, que já foram aplicadas antes de publicar
    private final String origin = UUID.randomUUID().toString();

    public WebSocketSessionRevoker(LocalWebSocketSessionRegistry sessions, StringRedisTemplate redis) {
        this.sessions = sessions;
        this.redis = redis;
    }

    /** Revoga também CONNECTs ainda pendentes; a reconexão continua permitida. */
    @Override
    public void revokeUser(UUID userId) {
        sessions.closeUser(userId);
        try {
            redis.convertAndSend(CHANNEL, origin + ":" + userId);
        } catch (RuntimeException failure) {
            log.warn("Revogação não chegou aos outros pods (userId={})", userId, failure);
        }
    }

    /** Revogação publicada por outro pod */
    @Override
    public void onMessage(Message message, byte[] pattern) {
        String[] parts = new String(message.getBody(), StandardCharsets.UTF_8).split(":", 2);
        if (parts.length != 2 || origin.equals(parts[0])) return;
        try {
            sessions.closeUser(UUID.fromString(parts[1]));
        } catch (IllegalArgumentException malformed) {
            log.warn("Revogação ignorada: corpo inválido no canal {}", CHANNEL);
        }
    }
}
