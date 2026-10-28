package com.solaria.messenger.core.service.message;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.solaria.messenger.core.model.Message;
import com.solaria.messenger.core.service.conversation.ConversationChangeNotifier;
import com.solaria.messenger.core.service.port.MessageEventPublisher;

/**
 * Notifica uma mudança de mensagem já persistida: o evento da mensagem e, em seguida,
 * a projeção atualizada da conversa para cada participante.
 */
@Service
public class MessageChangeNotifier {

    private static final Logger log = LoggerFactory.getLogger(MessageChangeNotifier.class);

    private final MessageEventPublisher messageEvents;
    private final ConversationChangeNotifier conversations;

    public MessageChangeNotifier(MessageEventPublisher messageEvents, ConversationChangeNotifier conversations) {
        this.messageEvents = messageEvents;
        this.conversations = conversations;
    }

    /** Anuncia uma mensagem nova e atualiza preview e não lidas da conversa. */
    public void notifyCreated(Message message) {
        messageEvents.messageCreated(message);
        publishConversationState(message.getConversationId());
    }

    /** Anuncia a edição efetiva e atualiza o preview da conversa. */
    public void notifyEdited(Message message) {
        messageEvents.messageEdited(message);
        publishConversationState(message.getConversationId());
    }

    /** Anuncia o tombstone e atualiza preview e não lidas da conversa. */
    public void notifyDeleted(Message message) {
        messageEvents.messageDeleted(message);
        publishConversationState(message.getConversationId());
    }

    /** A mensagem já foi persistida; uma falha ao invalidar a lista nunca vira erro do comando. */
    private void publishConversationState(String conversationId) {
        try {
            conversations.publishConversationChanged(conversationId);
        } catch (RuntimeException publicationFailure) {
            log.warn("Falha ao invalidar lista da conversa {} após alteração persistida", conversationId,
                    publicationFailure);
        }
    }
}
