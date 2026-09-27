package com.solaria.messenger.security;

import java.time.Instant;
import java.util.Map;
import java.util.Set;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import com.solaria.messenger.dto.ws.WsEventEnvelope;
import com.solaria.messenger.service.PresenceService;
import com.solaria.messenger.service.SessionRegistryService;
import com.solaria.messenger.service.TypingPresenceService;

/**
 * Reage ao fechamento de uma sessao STOMP 
 * <ol>
 *   <li>liberar a sessao no {@link SessionRegistryService} 
 *       se for a ultima ativa, dispara presenca OFFLINE via {@link PresenceService}</li>
 *   <li> se era a ultima ativa, encerra qualquer indicador de "digitando" do usuario ({@link TypingPresenceService})</li>
 * </ol>
 *
 */
@Component
public class WebSocketSessionEventListener {

    private static final Logger log = LoggerFactory.getLogger(WebSocketSessionEventListener.class);

    private static final String SESSION_ATTR_JWT = "jwt";

    private final SessionRegistryService sessionRegistryService;
    private final PresenceService presenceService;
    private final TypingPresenceService typingPresenceService;
    private final SimpMessagingTemplate simpMessagingTemplate;

    public WebSocketSessionEventListener(SessionRegistryService sessionRegistryService,
            PresenceService presenceService,
            TypingPresenceService typingPresenceService,
            SimpMessagingTemplate simpMessagingTemplate) {
        this.sessionRegistryService = sessionRegistryService;
        this.presenceService = presenceService;
        this.typingPresenceService = typingPresenceService;
        this.simpMessagingTemplate = simpMessagingTemplate;
    }

    @EventListener
    public void onSessionDisconnect(SessionDisconnectEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        String sessionId = accessor.getSessionId();
        String userId = resolveUserId(accessor);

        if (userId == null || sessionId == null) {
            // sessao nunca chegou a autenticar (CONNECT recusado/invalido) 
            return;
        }

        if (sessionRegistryService.release(userId, sessionId)) {
            presenceService.publishOffline(userId);

            Set<String> stuckTypingConversations = typingPresenceService.stopAllForUser(userId);
            for (String conversationId : stuckTypingConversations) {
                publishTypingStopped(conversationId, userId);
            }
        }
    }

    private String resolveUserId(StompHeaderAccessor accessor) {
        Map<String, Object> sessionAttributes = accessor.getSessionAttributes();
        if (sessionAttributes == null) {
            return null;
        }
        Object stored = sessionAttributes.get(SESSION_ATTR_JWT);
        return (stored instanceof Jwt jwt) ? jwt.getSubject() : null;
    }

    private void publishTypingStopped(String conversationId, String userId) {
        WsEventEnvelope envelope = WsEventEnvelope.builder()
                .type("TYPING_STOPPED")
                .conversationId(conversationId)
                .serverTimestamp(Instant.now())
                .payload(Map.of("userId", userId))
                .build();
        try {
            simpMessagingTemplate.convertAndSend("/topic/conversations/" + conversationId, envelope);
        } catch (Exception ex) {
            log.warn("Falha ao publicar TYPING_STOPPED automatico na desconexao (conversationId={}, userId={})",
                    conversationId, userId, ex);
        }
    }
}
