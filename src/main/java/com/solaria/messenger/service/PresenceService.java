package com.solaria.messenger.service;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import com.solaria.messenger.dto.ws.WsEventEnvelope;
import com.solaria.messenger.model.Conversation;
import com.solaria.messenger.repository.ConversationRepository;

/**
 * Presenca (ONLINE/OFFLINE) de usuarios, mostrada para todas as conversas de que participam
 */
@Service
public class PresenceService {

    private static final Logger log = LoggerFactory.getLogger(PresenceService.class);

    private final ConversationRepository conversationRepository;
    private final ObjectProvider<SimpMessagingTemplate> simpMessagingTemplateProvider;

    public PresenceService(ConversationRepository conversationRepository,
            ObjectProvider<SimpMessagingTemplate> simpMessagingTemplateProvider) {
        this.conversationRepository = conversationRepository;
        this.simpMessagingTemplateProvider = simpMessagingTemplateProvider;
    }

    /** Chamado quando {@code SessionRegistryService.tryAdmit} devolve {@code FIRST_SESSION} */
    public void publishOnline(String userId) {
        publishPresence(userId, "ONLINE");
    }

    /** Chamado quando {@code SessionRegistryService.release} devolve {@code true} */
    public void publishOffline(String userId) {
        publishPresence(userId, "OFFLINE");
    }

    private void publishPresence(String userId, String status) {
        try {
            UUID userUuid = UUID.fromString(userId);
            String type = "ONLINE".equals(status) ? "USER_ONLINE" : "USER_OFFLINE";
            List<Conversation> conversations = conversationRepository
                    .findByParticipantIdsContainingOrderByLastInteractionAtDesc(userUuid);

            for (Conversation conversation : conversations) {
                WsEventEnvelope envelope = WsEventEnvelope.builder()
                        .type(type)
                        .conversationId(conversation.getId())
                        .serverTimestamp(Instant.now())
                        .payload(Map.of("userId", userId))
                        .build();

                simpMessagingTemplateProvider.getObject()
                        .convertAndSend("/topic/conversations/" + conversation.getId(), envelope);
            }
        } catch (IllegalArgumentException ex) {
            log.warn("Presenca ignorada: userId nao e um UUID valido ({})", userId);
        } catch (Exception ex) {
            // Falha ao consultar conversas ou publicar 
            log.warn("Falha ao publicar presenca local {} (userId={})", status, userId, ex);
        }
    }
}
