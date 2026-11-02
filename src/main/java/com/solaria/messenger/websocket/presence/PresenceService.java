package com.solaria.messenger.websocket.presence;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.converter.MessageConverter;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import com.solaria.messenger.core.helper.ConversationAccessHelper;
import com.solaria.messenger.core.port.PresencePublisher;
import com.solaria.messenger.websocket.dto.response.PresenceSnapshotDTO;
import com.solaria.messenger.websocket.dto.response.WsEventEnvelope;
import com.solaria.messenger.websocket.event.SessionPresenceChanged;
import com.solaria.messenger.websocket.event.UserEventsSubscribed;
import com.solaria.messenger.websocket.security.StompDestinations;
import com.solaria.messenger.websocket.session.LocalWebSocketSessionRegistry;

/**
 * Service de presença do usuário (online/offline)
 *
 * <p>
 * Publica a presença no canal {@code events} de cada usuário, sem depender de ele ter aberto uma conversa. Presença é
 * da pessoa: quem recebe é cada contato, isto é, quem divide com ela alguma conversa que pode ler. Falhas não derrubam
 * a sessão.
 * </p>
 * <ul>
 *     <li>{@link #onUserEventsSubscribed(UserEventsSubscribed)} -> envia a uma nova assinatura o estado de presença de
 *         todos os contatos do usuário.</li>
 *     <li>{@link #onPresenceChanged(SessionPresenceChanged)} -> anuncia USER_ONLINE/USER_OFFLINE, uma vez, a cada
 *         contato do usuário.</li>
 *     <li>{@link #conversationJoined(String)} -> manda a cada participante de uma conversa que ganhou gente o estado dos
 *         outros participantes.</li>
 * </ul>
 */
@Service
public class PresenceService implements PresencePublisher {

    private static final Logger log = LoggerFactory.getLogger(PresenceService.class);

    private final ConversationAccessHelper authorization;
    private final LocalWebSocketSessionRegistry sessions;
    private final SimpMessagingTemplate messaging;
    private final MessageChannel outbound;
    private final MessageConverter converter;

    public PresenceService(ConversationAccessHelper authorization, LocalWebSocketSessionRegistry sessions,
            SimpMessagingTemplate messaging, @Qualifier("clientOutboundChannel") MessageChannel outbound,
            @Qualifier("brokerMessageConverter") MessageConverter converter) {
        this.authorization = authorization;
        this.sessions = sessions;
        this.messaging = messaging;
        this.outbound = outbound;
        this.converter = converter;
    }

    /** Envia só à assinatura nova o estado de presença de todos os contatos do usuário */
    @EventListener
    public void onUserEventsSubscribed(UserEventsSubscribed subscription) {
        try {
            // verifica se a sessão não foi revogada e se ela pertence ao user
            if (!sessions.isOperational(subscription.sessionId()) || !subscription.userId().equals(sessions.userId(subscription.sessionId())))
                return;
            // pega o atual estado de presença dos contatos e cria uma mensagem para ser enviada
            var snapshot = sessions.presenceSnapshot(subscription.subscriptionId(), authorization.contactsOf(subscription.userId()));
            var headers = SimpMessageHeaderAccessor.create(SimpMessageType.MESSAGE);
            headers.setSessionId(subscription.sessionId());
            headers.setSubscriptionId(subscription.subscriptionId());
            headers.setDestination(StompDestinations.userTopic(subscription.userId(), "events"));
            var message = converter.toMessage(envelope("PRESENCE_SNAPSHOT", snapshot), headers.getMessageHeaders());
            // envia a mensagem
            if (message == null || !outbound.send(message)) throw new IllegalStateException("Snapshot não enviado.");
        } catch (RuntimeException failure) {
            log.warn("Falha ao publicar snapshot privado (sessionId={})", subscription.sessionId(), failure);
        }
    }

    /**
     * Envia a mudança de presença a cada contato do usuário.
     *
     * <p>
     * shortcut: um aviso por contato, sem limite; numa comunidade de centenas de membros cada conexão ou queda gera
     * centenas de frames, limitar ou agrupar se o RabbitMQ sentir.
     * </p>
     */
    @EventListener
    public void onPresenceChanged(SessionPresenceChanged change) {
        try {
            // estado já mudou de novo: o evento mais novo (versão maior) é que vale
            if (sessions.isOnlineAnywhere(change.userId()) != change.online()) return;
            var payload = Map.of("userId", change.userId().toString(), "presenceEpoch", change.presenceEpoch(),
                    "presenceVersion", change.presenceVersion());
            var event = envelope(change.online() ? "USER_ONLINE" : "USER_OFFLINE", payload);
            for (UUID contact : authorization.contactsOf(change.userId())) {
                try {
                    messaging.convertAndSend(StompDestinations.userTopic(contact, "events"), event);
                } catch (RuntimeException failure) {
                    log.warn("Falha ao publicar presença a um contato (userId={})", change.userId(), failure);
                }
            }
        } catch (RuntimeException failure) {
            log.warn("Falha ao publicar presença local (userId={})", change.userId(), failure);
        }
    }

    /** Cada participante autorizado recebe o estado dos outros; quem já conhecia alguém só soma ao que tinha */
    @Override
    public void conversationJoined(String conversationId) {
        Set<UUID> participants = authorization.authorizedParticipants(Set.of(conversationId))
                .getOrDefault(conversationId, Set.of());
        if (participants.isEmpty()) return;
        PresenceSnapshotDTO full = sessions.presenceSnapshot(null, participants);
        for (UUID recipient : participants) {
            try {
                Map<UUID, Boolean> others = new HashMap<>(full.users());
                others.remove(recipient);
                var snapshot = new PresenceSnapshotDTO(null, full.presenceEpoch(), full.presenceVersion(), others);
                messaging.convertAndSend(StompDestinations.userTopic(recipient, "events"),
                        envelope("PRESENCE_SNAPSHOT", snapshot));
            } catch (RuntimeException failure) {
                log.warn("Falha ao publicar retrato parcial (conversationId={})", conversationId, failure);
            }
        }
    }

    /** Presença é da pessoa, não de uma conversa: o envelope vai sem conversationId */
    private static WsEventEnvelope envelope(String type, Object payload) {
        return WsEventEnvelope.builder().type(type)
                .eventId(UUID.randomUUID().toString()).serverTimestamp(Instant.now()).payload(payload).build();
    }
}
