package com.solaria.messenger.core.service.message;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataRetrievalFailureException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;

import com.solaria.messenger.core.contract.request.ChatbotMessageRequestDTO;
import com.solaria.messenger.core.contract.request.MessageRequestDTO;
import com.solaria.messenger.core.contract.response.MessageResponseDTO;
import com.solaria.messenger.core.exception.BusinessException;
import com.solaria.messenger.core.exception.InvalidFieldException;
import com.solaria.messenger.core.exception.MessageSendFailedException;
import com.solaria.messenger.core.model.Conversation;
import com.solaria.messenger.core.model.Message;
import com.solaria.messenger.core.model.enums.ConversationType;
import com.solaria.messenger.core.model.enums.MessageType;
import com.solaria.messenger.core.repository.ConversationRepository;
import com.solaria.messenger.core.repository.MessageRepository;
import com.solaria.messenger.core.security.rbac.RbacAuthorizationService;
import com.solaria.messenger.core.service.ConversationAuthorizationService;
import com.solaria.messenger.core.service.ConversationMutationGuard;

import static com.solaria.messenger.core.mapper.MessageMapper.toResponse;

/**
 * Service de envio de mensagens
 *
 * <p>
 * Grava mensagens novas vindas do user (REST ou STOMP) ou do chatbot e só avisa os participantes
 * depois que o insert foi confirmado.
 * </p>
 * <ul>
 *     <li>{@link #sendUserMessage(MessageRequestDTO)} -> grava a mensagem enviada pelo user.</li>
 *     <li>{@link #ingestChatbotMessage(ChatbotMessageRequestDTO)} -> grava a resposta do chatbot.</li>
 * </ul>
 */
@Service
public class MessageSendService {

    private static final Logger log = LoggerFactory.getLogger(MessageSendService.class);
    private static final String ROLE_USER = "user";
    private static final String ROLE_ASSISTANT = "assistant";
    private final MessageRepository messageRepository;
    private final ConversationRepository conversations;
    private final RbacAuthorizationService rbac;
    private final MessageChangeNotifier messageChangeNotifier;
    private final ConversationAuthorizationService resourceAccess;
    private final ConversationMutationGuard guard;

    public MessageSendService(MessageRepository messages, ConversationRepository conversations,
            RbacAuthorizationService rbac, MessageChangeNotifier notifier,
            ConversationAuthorizationService authorization, ConversationMutationGuard guard) {
        this.messageRepository = messages;
        this.conversations = conversations;
        this.rbac = rbac;
        this.messageChangeNotifier = notifier;
        this.resourceAccess = authorization;
        this.guard = guard;
    }

    /** Grava a mensagem do user; cada chamada é um insert novo, nunca reaproveita um anterior */
    public MessageResponseDTO sendUserMessage(MessageRequestDTO dto) {
        UUID clientMessageId = parseClientMessageId(dto.getClientMessageId());
        if (dto.getMessageType() == MessageType.CHATBOT_TO_USER) {
            throw new InvalidFieldException(
                    "messageType CHATBOT_TO_USER só pode ser publicado pelo pipeline de LLM (POST /internal/messages).");
        }

        Message saved = guard.execute(() -> resourceAccess.withAccess(dto.getConversationId(), rbac.currentUserId(),
                conversation -> sendUserMessageAuthorized(conversation, dto, clientMessageId)));

        messageChangeNotifier.notifyCreated(saved);
        return toResponse(saved);
    }

    /** Grava a resposta do chatbot (pipeline interno) numa conversa CHAT_BOT */
    public MessageResponseDTO ingestChatbotMessage(ChatbotMessageRequestDTO dto) {
        Message saved = guard.execute(() -> {
            Conversation conversation = resourceAccess.requireEntityById(dto.getConversationId());
            if (conversation.getConversationType() != ConversationType.CHAT_BOT) {
                throw new InvalidFieldException("Mensagens do chatbot só podem ser publicadas em conversas do tipo CHAT_BOT.");
            }
            // a autorização é feita em nome do dono da conversa
            return resourceAccess.withAccess(dto.getConversationId(), conversation.getCreatedBy(),
                    current -> ingestChatbotMessageAuthorized(current, dto));
        });

        messageChangeNotifier.notifyCreated(saved);
        return toResponse(saved);
    }

    /** Confere conversa ativa, tipo da mensagem e comunidade ativa, e grava */
    private Message sendUserMessageAuthorized(Conversation conversation, MessageRequestDTO dto, UUID clientMessageId) {
        resourceAccess.requireActive(conversation);
        requireMessageTypeMatchesConversation(dto.getMessageType(), conversation.getConversationType());
        resourceAccess.requireCommunityWriter(conversation, rbac.currentUserId());

        Message message = new Message();
        message.setConversationId(conversation.getId());
        message.setSenderId(rbac.currentUserId());
        message.setClientMessageId(clientMessageId);
        message.setRole(ROLE_USER);
        message.setMessageType(dto.getMessageType());
        message.setContent(dto.getContent());
        message.setTimestamp(Instant.now());
        return appendAndTouch(conversation, message);
    }

    /** Grava a resposta do assistente na conversa CHAT_BOT ativa */
    private Message ingestChatbotMessageAuthorized(Conversation conversation, ChatbotMessageRequestDTO dto) {
        resourceAccess.requireActive(conversation);

        Message message = new Message();
        message.setConversationId(conversation.getId());
        message.setRole(ROLE_ASSISTANT);
        message.setMessageType(MessageType.CHATBOT_TO_USER);
        message.setContent(dto.getContent());
        message.setMetadata(dto.getMetadata());
        message.setTimestamp(Instant.now());
        return appendAndTouch(conversation, message);
    }

    /** Grava a mensagem e atualiza a última interação da conversa */
    private Message appendAndTouch(Conversation conversation, Message message) {
        Message saved = append(message);
        updateInteractionAfterInsert(conversation, saved);
        return saved;
    }

    /** clientMessageId é opcional, mas se vier precisa ser um UUID canônico */
    private UUID parseClientMessageId(String value) {
        if (value == null) return null;
        if (!value.matches("[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}")) {
            throw new InvalidFieldException("clientMessageId deve ser um UUID canônico quando informado.");
        }
        return UUID.fromString(value);
    }

    /** Insere com a próxima sequência; cada tipo de falha vira um erro com status e código próprios */
    private Message append(Message message) {
        // só o repository repete em colisão de sequência; aqui nenhuma falha reinicia o envio
        try {
            return messageRepository.insertWithPersistedSequence(message);
        } catch (DataRetrievalFailureException unavailable) {
            throw sendFailure(message, HttpStatus.SERVICE_UNAVAILABLE, "DEPENDENCY_UNAVAILABLE",
                    "Não foi possível consultar a sequência; esta tentativa não inseriu mensagem.", unavailable);
        } catch (OptimisticLockingFailureException exhausted) {
            throw sendFailure(message, HttpStatus.CONFLICT, "CONCURRENT_MODIFICATION",
                    "Não foi possível alocar uma sequência; esta tentativa não inseriu mensagem.", exhausted);
        } catch (DuplicateKeyException rejected) {
            throw sendFailure(message, HttpStatus.CONFLICT, "DATA_INTEGRITY_VIOLATION",
                    "O banco rejeitou a escrita por uma restrição de integridade.", rejected);
        } catch (RuntimeException uncertain) {
            throw sendFailure(message, HttpStatus.SERVICE_UNAVAILABLE, "MESSAGE_PERSISTENCE_UNCERTAIN",
                    "Não foi possível confirmar a persistência. Consulte o histórico antes de reenviar.", uncertain);
        }
    }

    /** Erro de envio com status e código, levando o clientMessageId para o app correlacionar */
    private BusinessException sendFailure(Message message, HttpStatus status, String code,
            String detail, RuntimeException cause) {
        Map<String, Object> correlation = message.getClientMessageId() == null ? Map.of()
                : Map.of("clientMessageId", message.getClientMessageId().toString());
        BusinessException failure = new MessageSendFailedException(detail, status, code, correlation);
        failure.addSuppressed(cause);
        return failure;
    }

    /** A mensagem já foi gravada; se isto falhar, só gera log e a resposta segue normal */
    private void updateInteractionAfterInsert(Conversation conversation, Message saved) {
        try {
            conversation.setLastInteractionAt(saved.getTimestamp());
            conversations.updateLastInteraction(conversation.getId(), saved.getTimestamp());
        } catch (RuntimeException failure) {
            log.warn("Mensagem persistida; falha ao atualizar interação (messageId={}, conversationId={})",
                    saved.getId(), saved.getConversationId(), failure);
        }
    }

    /** O tipo da mensagem precisa combinar com a conversa: GROUP -> USER_TO_GROUP, DIRECT -> USER_TO_USER, CHAT_BOT -> USER_TO_CHATBOT */
    private void requireMessageTypeMatchesConversation(MessageType messageType, ConversationType conversationType) {
        MessageType expected = switch (conversationType) {
            case GROUP -> MessageType.USER_TO_GROUP;
            case DIRECT -> MessageType.USER_TO_USER;
            case CHAT_BOT -> MessageType.USER_TO_CHATBOT;
        };
        if (messageType != expected) {
            throw new InvalidFieldException(
                    "messageType " + messageType + " não é válido para uma conversa do tipo " + conversationType + ".");
        }
    }
}
