package com.solaria.messenger.websocket.publisher;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import com.solaria.messenger.core.contract.response.ConversationListItemResponseDTO;
import com.solaria.messenger.core.service.port.ConversationEventPublisher;
import com.solaria.messenger.websocket.dto.response.WsEventEnvelope;
import com.solaria.messenger.websocket.security.StompDestinations;

/** Entrega o estado das conversas no tópico privado {@code /topic/users.{userId}.conversations} de cada destinatário. */
@Component
public class StompConversationEventPublisher implements ConversationEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(StompConversationEventPublisher.class);
    private final SimpMessagingTemplate messaging;

    public StompConversationEventPublisher(SimpMessagingTemplate messaging) {
        this.messaging = messaging;
    }

    /** O payload é a mesma projeção da consulta REST; o interceptor de saída ainda verifica cada sessão. */
    @Override
    public void conversationUpdated(UUID recipient, ConversationListItemResponseDTO item) {
        send(recipient, WsEventEnvelope.builder().type("CONVERSATION_UPDATED")
                .conversationId(item.id()).eventId(UUID.randomUUID().toString())
                .serverTimestamp(Instant.now()).payload(item).build());
    }

    /** Só informa o ID já conhecido da participação perdida e o marcador de sincronização. */
    @Override
    public void conversationRemoved(UUID recipient, String conversationId, String syncEpoch, long syncVersion) {
        send(recipient, WsEventEnvelope.builder().type("CONVERSATION_REMOVED")
                .eventId(UUID.randomUUID().toString()).serverTimestamp(Instant.now())
                .payload(Map.of("id", conversationId, "syncEpoch", syncEpoch, "syncVersion", syncVersion)).build());
    }

    /** O comando já persistiu; uma falha de entrega não repete nem desfaz a escrita. */
    private void send(UUID recipient, WsEventEnvelope event) {
        try {
            messaging.convertAndSend(StompDestinations.userTopic(recipient, "conversations"), event);
        } catch (RuntimeException failure) {
            log.warn("Falha ao publicar evento de conversa (eventId={}, type={})", event.getEventId(), event.getType(), failure);
        }
    }
}
