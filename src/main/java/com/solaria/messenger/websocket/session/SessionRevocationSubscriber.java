package com.solaria.messenger.websocket.session;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.ChannelTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import jakarta.annotation.PreDestroy;

/**
 * Assina o canal de revogação fora do boot.
 *
 * <p>
 * O container não é um bean de propósito: no start ele espera a assinatura e lança exceção se o Redis estiver fora,
 * o que derrubaria o pod. Aqui a falha vira log e uma nova tentativa a cada 30s.
 * </p>
 *
 * <p><b>Depende de:</b></p>
 * <ul>
 *     <li>{@link RedisConnectionFactory} -> conexão com o Redis (Upstash).</li>
 *     <li>{@link WebSocketSessionRevoker} -> ouvinte que fecha as sessões do user neste pod.</li>
 * </ul>
 */
@Component
class SessionRevocationSubscriber {

    private static final Logger log = LoggerFactory.getLogger(SessionRevocationSubscriber.class);
    private final RedisMessageListenerContainer container = new RedisMessageListenerContainer();

    SessionRevocationSubscriber(RedisConnectionFactory redis, WebSocketSessionRevoker revoker) {
        container.setConnectionFactory(redis);
        container.addMessageListener(revoker, new ChannelTopic(WebSocketSessionRevoker.CHANNEL));
        // criado com new: o ciclo de vida do Spring não roda, então o init e o destroy são chamados aqui
        container.afterPropertiesSet();
    }

    /** Assina se ainda não estiver assinado; depois disso, quedas de conexão o próprio container recupera */
    @Scheduled(fixedDelay = 30_000)
    void listen() {
        if (container.isListening()) return;
        try {
            container.stop();
            container.start();
        } catch (RuntimeException failure) {
            log.warn("Canal de revogação indisponível; nova tentativa em 30s", failure);
        }
    }

    @PreDestroy
    void close() throws Exception {
        container.destroy();
    }
}
