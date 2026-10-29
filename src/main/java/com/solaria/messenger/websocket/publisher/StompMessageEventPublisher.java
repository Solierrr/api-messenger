package com.solaria.messenger.websocket.publisher;

import java.time.Instant;
import java.util.Set;
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

/**
 * Publica os eventos de mensagem no tópico privado {@code /topic/users.{userId}.events} de cada destinatário.
 *
 * <p>
 * O tópico da conversa ({@code /topic/conversations.{id}}) fica só com presença, digitação e o snapshot de presença.
 * O envelope e o {@code eventId} são montados uma vez por evento, então todos os
 * destinatários recebem o mesmo frame.
 * </p>
 */
@Component
public class StompMessageEventPublisher implements MessageEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(StompMessageEventPublisher.class);
    private final SimpMessagingTemplate messaging;

    public StompMessageEventPublisher(SimpMessagingTemplate messaging) {
        this.messaging = messaging;
    }

    /** O eventId da criação é o próprio id da mensagem, permitindo deduplicar com o ACK. */
    @Override
    public void messageCreated(Message message, Set<UUID> recipients) {
        publish(message, recipients, "MESSAGE_CREATED", message.getId());
    }

    /** Edições recebem um eventId novo a cada alteração efetiva. */
    @Override
    public void messageEdited(Message message, Set<UUID> recipients) {
        publish(message, recipients, "MESSAGE_EDITED", UUID.randomUUID().toString());
    }

    /** Exclusões recebem um eventId novo; o payload já é o tombstone sem conteúdo. */
    @Override
    public void messageDeleted(Message message, Set<UUID> recipients) {
        publish(message, recipients, "MESSAGE_DELETED", UUID.randomUUID().toString());
    }

    /**
     * Monta o envelope uma vez e envia ao tópico de eventos de cada destinatário. A mensagem já foi persistida, então
     * uma falha com um destinatário vira log e não impede os demais nem vira 5XX.
     */
    private void publish(Message message, Set<UUID> recipients, String type, String eventId) {
        WsEventEnvelope envelope = WsEventEnvelope.builder()
                .type(type)
                .conversationId(message.getConversationId())
                .eventId(eventId)
                .serverTimestamp(Instant.now())
                .payload(toResponse(message))
                .build();
        for (UUID recipient : recipients) {
            try {
                messaging.convertAndSend(StompDestinations.userTopic(recipient, "events"), envelope);
            } catch (RuntimeException ex) {
                log.warn("Falha ao publicar {} da mensagem {} (conversationId={}, recipient={});",
                        type, message.getId(), message.getConversationId(), recipient, ex);
            }
        }
    }
}
