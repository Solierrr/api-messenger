package com.solaria.messenger.core.usecase.message;

import java.time.Instant;

import org.springframework.stereotype.Service;

import com.solaria.messenger.core.contract.request.ChatbotMessageRequestDTO;
import com.solaria.messenger.core.contract.response.MessageResponseDTO;
import com.solaria.messenger.core.exception.InvalidFieldException;
import com.solaria.messenger.core.helper.ConversationAccessHelper;
import com.solaria.messenger.core.helper.LookupHelper;
import com.solaria.messenger.core.helper.MessageWriteHelper;
import com.solaria.messenger.core.helper.NotificationHelper;
import com.solaria.messenger.core.model.Conversation;
import com.solaria.messenger.core.model.Message;
import com.solaria.messenger.core.model.enums.ConversationType;
import com.solaria.messenger.core.model.enums.MessageType;
import com.solaria.messenger.core.support.ConversationMutationGuard;

import static com.solaria.messenger.core.helper.PureRulesHelper.requireConversationActive;
import static com.solaria.messenger.core.mapper.MessageMapper.toResponse;

/**
 * Caso de uso que grava a resposta do chatbot em uma conversa {@code CHAT_BOT}.
 *
 * <p>
 * É chamado pelo pipeline de LLM. A autorização é feita em nome do criador da conversa. A mensagem entra com papel
 * {@code assistant}. Os clientes recebem o aviso de criação depois que a gravação termina.
 * </p>
 *
 * <p>
 * <b>Depende de:</b>
 * </p>
 * <ul>
 *     <li>{@link LookupHelper} -> carrega a conversa.</li>
 *     <li>{@link ConversationAccessHelper} -> confere a participação do criador.</li>
 *     <li>{@link MessageWriteHelper} -> grava a mensagem e atualiza a última interação.</li>
 *     <li>{@link NotificationHelper} -> avisa a criação da mensagem.</li>
 *     <li>{@link ConversationMutationGuard} -> serializa a operação na JVM.</li>
 * </ul>
 *
 * <p>
 * Principal operação disponibilizada:
 * </p>
 * <ul>
 *     <li>{@link #execute(ChatbotMessageRequestDTO)} -> grava a resposta do chatbot.</li>
 * </ul>
 */
@Service
public class IngestChatbotMessageUseCase {

    private static final String ROLE_ASSISTANT = "assistant";
    private final LookupHelper lookup;
    private final ConversationAccessHelper access;
    private final MessageWriteHelper writes;
    private final NotificationHelper notifications;
    private final ConversationMutationGuard guard;

    public IngestChatbotMessageUseCase(LookupHelper lookup, ConversationAccessHelper access, MessageWriteHelper writes,
            NotificationHelper notifications, ConversationMutationGuard guard) {
        this.lookup = lookup;
        this.access = access;
        this.writes = writes;
        this.notifications = notifications;
        this.guard = guard;
    }

    /**
     * Grava a resposta do chatbot na conversa.
     *
     * @param dto conteúdo, metadados e identificador da conversa.
     * @return mensagem gravada.
     * @throws InvalidFieldException caso a conversa não seja do tipo {@code CHAT_BOT}.
     */
    public MessageResponseDTO execute(ChatbotMessageRequestDTO dto) {
        Message saved = guard.execute(() -> {
            Conversation conversation = lookup.requireConversation(dto.getConversationId());
            if (conversation.getConversationType() != ConversationType.CHAT_BOT) {
                throw new InvalidFieldException("Mensagens do chatbot só podem ser publicadas em conversas do tipo CHAT_BOT.");
            }
            // a autorização é feita em nome do dono da conversa
            return access.withAccess(dto.getConversationId(), conversation.getCreatedBy(),
                    current -> ingestAuthorized(current, dto));
        });

        notifications.notifyMessageCreated(saved);
        return toResponse(saved);
    }

    /** Grava a resposta do assistente na conversa CHAT_BOT ativa */
    private Message ingestAuthorized(Conversation conversation, ChatbotMessageRequestDTO dto) {
        requireConversationActive(conversation);

        Message message = new Message();
        message.setConversationId(conversation.getId());
        message.setRole(ROLE_ASSISTANT);
        message.setMessageType(MessageType.CHATBOT_TO_USER);
        message.setContent(dto.getContent());
        message.setMetadata(dto.getMetadata());
        message.setTimestamp(Instant.now());
        return writes.appendAndTouch(conversation, message);
    }
}
