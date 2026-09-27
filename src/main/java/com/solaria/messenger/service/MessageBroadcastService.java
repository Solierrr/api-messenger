package com.solaria.messenger.service;

import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import com.solaria.messenger.dto.response.MessageResponseDTO;
import com.solaria.messenger.dto.ws.WsEventEnvelope;
import com.solaria.messenger.model.Message;

/**
 * publica mensagens que já foram enviadas para o BD
 * no tópico ({@code /topic/conversations/{conversationId}}).
 *
 */
@Service
public class MessageBroadcastService {

    private static final Logger log = LoggerFactory.getLogger(MessageBroadcastService.class);

    private final SimpMessagingTemplate simpMessagingTemplate;

    public MessageBroadcastService(SimpMessagingTemplate simpMessagingTemplate) {
        this.simpMessagingTemplate = simpMessagingTemplate;
    }

    public void broadcastInline(Message message) {
        WsEventEnvelope envelope = WsEventEnvelope.builder()
                .type("MESSAGE_CREATED")
                .conversationId(message.getConversationId())
                .eventId(message.getId())
                .serverTimestamp(Instant.now())
                .payload(toResponse(message))
                .build();

        try {
            simpMessagingTemplate.convertAndSend("/topic/conversations/" + message.getConversationId(), envelope);
        } catch (RuntimeException ex) {
            // A mensagem já foi persistida, uma falha aqui nunca vira 5XX
            log.warn("Falha ao publicar broadcast WS da mensagem {} (conversationId={});",
                    message.getId(), message.getConversationId(), ex);
        }
    }

    private MessageResponseDTO toResponse(Message message) {
        return MessageResponseDTO.builder()
                .id(message.getId())
                .conversationId(message.getConversationId())
                .senderId(message.getSenderId())
                .role(message.getRole())
                .messageType(message.getMessageType())
                .content(message.getContent())
                .metadata(message.getMetadata())
                .timestamp(message.getTimestamp())
                .sequence(message.getSequence())
                .build();
    }
}
