package com.solaria.messenger.websocket.publisher;

import java.time.Instant;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

import com.solaria.messenger.core.model.Message;
import com.solaria.messenger.core.port.MessageEventPublisher;
import com.solaria.messenger.websocket.dto.response.WsEventEnvelope;
import com.solaria.messenger.websocket.security.StompDestinations;

import static com.solaria.messenger.core.mapper.MessageMapper.toResponse;

/** Publica eventos de mensagem no tópico {@code /topic/conversations.{conversationId}}. */
@Component
public class StompMessageEventPublisher implements MessageEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(StompMessageEventPublisher.class);
    private final SimpMessagingTemplate messaging;

    public StompMessageEventPublisher(SimpMessagingTemplate messaging) {
        this.messaging = messaging;
    }

    /** O eventId da criação é o próprio id da mensagem, permitindo deduplicar com o ACK. */
    @Override
    public void messageCreated(Message message) {
        publish(message, "MESSAGE_CREATED", message.getId());
    }

    /** Edições recebem um eventId novo a cada alteração efetiva. */
    @Override
    public void messageEdited(Message message) {
        publish(message, "MESSAGE_EDITED", UUID.randomUUID().toString());
    }

    /** Exclusões recebem um eventId novo; o payload já é o tombstone sem conteúdo. */
    @Override
    public void messageDeleted(Message message) {
        publish(message, "MESSAGE_DELETED", UUID.randomUUID().toString());
    }

    /** Monta o envelope e envia ao tópico; a mensagem já foi persistida, então uma falha aqui nunca vira 5XX. */
    private void publish(Message message, String type, String eventId) {
        WsEventEnvelope envelope = WsEventEnvelope.builder()
                .type(type)
                .conversationId(message.getConversationId())
                .eventId(eventId)
                .serverTimestamp(Instant.now())
                .payload(toResponse(message))
                .build();
        try {
            messaging.convertAndSend(StompDestinations.conversationTopic(message.getConversationId()), envelope);
        } catch (RuntimeException ex) {
            log.warn("Falha ao publicar {} da mensagem {} (conversationId={});",
                    type, message.getId(), message.getConversationId(), ex);
        }
    }
}
