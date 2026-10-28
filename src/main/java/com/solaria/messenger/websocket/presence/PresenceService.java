package com.solaria.messenger.websocket.presence;

import java.time.Instant;
import java.util.List;
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

import com.solaria.messenger.core.model.Conversation;
import com.solaria.messenger.core.repository.ConversationRepository;
import com.solaria.messenger.core.helper.ConversationAccessHelper;
import com.solaria.messenger.websocket.dto.response.WsEventEnvelope;
import com.solaria.messenger.websocket.event.ConversationSubscriptionRegistered;
import com.solaria.messenger.websocket.security.StompDestinations;
import com.solaria.messenger.websocket.session.LocalWebSocketSessionRegistry;
import com.solaria.messenger.websocket.event.SessionPresenceChanged;

/**
 * Service de presença do usuário (online/offline)
 *
 * <p>
 * A partir de eventos da sessão webscoket, publica um STOMP para os participantes autorizados, mostrando presença do user.
 * falhas não derrubam a sessão.
 * </p>
 * <ul>
 *     <li>{@link #onSubscriptionRegistered(ConversationSubscriptionRegistered)} -> envia o atual estado de presença dos user para uma nova sessão.</li>
 *     <li>{@link #onPresenceChanged(SessionPresenceChanged)} -> anuncia USER_ONLINE/USER_OFFLINE nas conversas do usuário.</li>
 * </ul>
 */
@Service
public class PresenceService {

    private static final Logger log = LoggerFactory.getLogger(PresenceService.class);
    private final ConversationRepository conversations;
    private final ConversationAccessHelper authorization;
    private final LocalWebSocketSessionRegistry sessions;
    private final SimpMessagingTemplate messaging;
    private final MessageChannel outbound;
    private final MessageConverter converter;

    public PresenceService(ConversationRepository conversations, ConversationAccessHelper authorization,
            LocalWebSocketSessionRegistry sessions, SimpMessagingTemplate messaging,
            @Qualifier("clientOutboundChannel") MessageChannel outbound,
            @Qualifier("brokerMessageConverter") MessageConverter converter) {
        this.conversations = conversations;
        this.authorization = authorization;
        this.sessions = sessions;
        this.messaging = messaging;
        this.outbound = outbound;
        this.converter = converter;
    }

    /** Envia o atual estado se prensença dos users qualificados para uma nova sessão */
    @EventListener
    public void onSubscriptionRegistered(ConversationSubscriptionRegistered subscription) {
        try {

            // verifica se a sessão não foi revogada e se ela pertence ao user
            if (!sessions.isOperational(subscription.sessionId()) || !subscription.userId().equals(sessions.userId(subscription.sessionId())))
             return;

            // verifica se a conversa existe no bd
            Conversation current = conversations.findById(subscription.conversationId()).orElse(null);
            if (current == null) return;

            // pega os participantes da conversa e verifica se o user faz parte
            Set<UUID> participants = authorization.authorizedParticipants(List.of(current))
                    .getOrDefault(current.getId(), Set.of());
            if (!participants.contains(subscription.userId())) return;

            // pega o atual estado de presença e cria uma mensagem para ser enviada
            var snapshot = sessions.presenceSnapshot(subscription.subscriptionId(), participants);
            var headers = SimpMessageHeaderAccessor.create(SimpMessageType.MESSAGE);
            headers.setSessionId(subscription.sessionId());
            headers.setSubscriptionId(subscription.subscriptionId());
            headers.setDestination(StompDestinations.conversationTopic(current.getId()));
            var message = converter.toMessage(envelope("PRESENCE_SNAPSHOT", current.getId(), snapshot),
                    headers.getMessageHeaders());

            // envia a mensagem
            if (message == null || !outbound.send(message)) throw new IllegalStateException("Snapshot não enviado.");

        } catch (RuntimeException failure) {
            log.warn("Falha ao publicar snapshot privado (sessionId={}, conversationId={})",
                    subscription.sessionId(), subscription.conversationId(), failure);
        }
    }

    /** envia a mudança de presença em todas as conversas do usuário */
    @EventListener
    public void onPresenceChanged(SessionPresenceChanged change) {
        try {
            // estado já mudou de novo: o evento mais novo (versão maior) é que vale
            if (sessions.isOnlineAnywhere(change.userId()) != change.online()) return;

            // procura as conversas que o user participa
            List<Conversation> current = conversations.findByParticipantIdsContainingOrderByLastInteractionAtDesc(change.userId());
            // verifica se o user está apto a conversa
            Map<String, Set<UUID>> authorized = authorization.authorizedParticipants(current);

            for (Conversation conversation : current) {
                // verifica se o user ainda está na conversa
                if (!authorized.getOrDefault(conversation.getId(), Set.of()).contains(change.userId())) continue;

                // monta e envia mensagem
                var payload = Map.of("userId", change.userId().toString(), "presenceEpoch", change.presenceEpoch(),
                        "presenceVersion", change.presenceVersion());
                        
                messaging.convertAndSend(StompDestinations.conversationTopic(conversation.getId()),
                        envelope(change.online() ? "USER_ONLINE" : "USER_OFFLINE", conversation.getId(), payload));
            }

        } catch (RuntimeException failure) {
            log.warn("Falha ao publicar presença local (userId={})", change.userId(), failure);
        }
    }

    private static WsEventEnvelope envelope(String type, String conversationId, Object payload) {
        return WsEventEnvelope.builder().type(type).conversationId(conversationId)
                .eventId(UUID.randomUUID().toString()).serverTimestamp(Instant.now()).payload(payload).build();
    }
}
