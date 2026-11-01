package com.solaria.messenger.websocket.presence;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import com.solaria.messenger.core.model.Conversation;
import com.solaria.messenger.core.helper.ConversationAccessHelper;
import com.solaria.messenger.core.helper.LookupHelper;
import com.solaria.messenger.websocket.dto.response.WsEventEnvelope;
import com.solaria.messenger.websocket.security.StompDestinations;
import com.solaria.messenger.websocket.session.LocalWebSocketSessionRegistry;
import com.solaria.messenger.websocket.event.SessionPresenceChanged;

/**
 * Service do indicador "digitando..."
 *
 * <p>
 * Confere se o user participa da conversa, atualiza o estado local de typing e publica no tópico da conversa.
 * </p>
 * <ul>
 *     <li>{@link #start(String, UUID)} -> avisa que o user começou a digitar.</li>
 *     <li>{@link #stop(String, UUID)} -> avisa que o user parou de digitar.</li>
 *     <li>{@link #onPresenceChanged(SessionPresenceChanged)} -> encerra o typing quando o user fica offline.</li>
 * </ul>
 */
@Service
public class TypingService {

    private static final Logger log = LoggerFactory.getLogger(TypingService.class);
    private final ConversationAccessHelper authorization;
    private final LookupHelper lookup;
    private final TypingPresenceService typingPresence;
    private final LocalWebSocketSessionRegistry sessions;
    private final SimpMessagingTemplate messaging;

    public TypingService(ConversationAccessHelper authorization, LookupHelper lookup, TypingPresenceService typingPresence,
            LocalWebSocketSessionRegistry sessions, SimpMessagingTemplate messaging) {
        this.authorization = authorization;
        this.lookup = lookup;
        this.typingPresence = typingPresence;
        this.sessions = sessions;
        this.messaging = messaging;
    }

    /** Avisa o início do typing; repetições dentro de 2s não consultam o banco nem publicam de novo */
    public void start(String conversationId, UUID actor) {
        String userId = actor.toString();
        if (typingPresence.startedRecently(conversationId, userId)) return;

        // participante de conversa com comunidade ativa
        Conversation conversation = lookup.requireConversation(conversationId);
        authorization.requireParticipant(conversation, actor);
        authorization.requireCommunityActive(conversation);

        typingPresence.start(conversation.getId(), userId);
        publish(conversation.getId(), userId, "TYPING_STARTED");
    }

    /** Avisa o fim do typing; funciona mesmo com a comunidade arquivada */
    public void stop(String conversationId, UUID actor) {
        Conversation conversation = lookup.requireConversation(conversationId);
        authorization.requireParticipant(conversation, actor);

        String userId = actor.toString();
        typingPresence.stop(conversation.getId(), userId);
        publish(conversation.getId(), userId, "TYPING_STOPPED");
    }

    /**
     * Encerra o typing quando a última sessão do user neste pod cai; o typing nasce no pod da sessão,
     * então vale a visão local mesmo com o user online em outro pod
     */
    @EventListener
    public void onPresenceChanged(SessionPresenceChanged change) {
        if (sessions.isOnline(change.userId())) return;

        // para o typing em todas as conversas; falha numa não impede as outras
        String userId = change.userId().toString();
        for (String conversationId : typingPresence.stopAllForUser(userId)) {
            try {
                publish(conversationId, userId, "TYPING_STOPPED");
            } catch (RuntimeException failure) {
                log.warn("Falha ao publicar fim de typing (conversationId={})", conversationId, failure);
            }
        }
    }

    private void publish(String conversationId, String userId, String type) {
        WsEventEnvelope envelope = WsEventEnvelope.builder()
                .type(type)
                .conversationId(conversationId)
                .serverTimestamp(Instant.now())
                .payload(Map.of("userId", userId))
                .build();
        messaging.convertAndSend(StompDestinations.conversationTopic(conversationId), envelope);
    }
}
