package com.solaria.messenger.service;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Service;

import com.solaria.messenger.dto.request.ChatbotMessageRequestDTO;
import com.solaria.messenger.dto.request.MessageRequestDTO;
import com.solaria.messenger.dto.response.MessageResponseDTO;
import com.solaria.messenger.exception.InvalidFieldException;
import com.solaria.messenger.model.Conversation;
import com.solaria.messenger.model.Message;
import com.solaria.messenger.model.enums.ConversationType;
import com.solaria.messenger.model.enums.MessageType;
import com.solaria.messenger.repository.MessageRepository;
import com.solaria.messenger.security.rbac.RbacAuthorizationService;

@Service
public class MessageService {


    private static final String ROLE_USER = "user";
    private static final String ROLE_ASSISTANT = "assistant";

    private static final int DEFAULT_MESSAGE_PAGE_SIZE = 100;
    private static final int MAX_MESSAGE_PAGE_SIZE = 500;

    private final MessageRepository messageRepository;
    private final ConversationService conversationService;
    private final RbacAuthorizationService rbac;
    private final MessageBroadcastService messageBroadcastService;

    public MessageService(MessageRepository messageRepository,
            ConversationService conversationService,
            RbacAuthorizationService rbac,
            MessageBroadcastService messageBroadcastService) {
        this.messageRepository = messageRepository;
        this.conversationService = conversationService;
        this.rbac = rbac;
        this.messageBroadcastService = messageBroadcastService;
    }


    public MessageResponseDTO sendUserMessage(MessageRequestDTO dto) {
        if (dto.getMessageType() == MessageType.CHATBOT_TO_USER) {
            throw new InvalidFieldException(
                    "messageType CHATBOT_TO_USER só pode ser publicado pelo pipeline de LLM (POST /internal/messages).");
        }

        Conversation conversation = conversationService.requireEntityById(dto.getConversationId());
        conversationService.requireParticipant(conversation);
        conversationService.requireActive(conversation);
        requireMessageTypeMatchesConversation(dto.getMessageType(), conversation.getConversationType());

        Message message = new Message();
        message.setConversationId(dto.getConversationId());
        message.setSenderId(rbac.currentUserId());
        message.setRole(ROLE_USER);
        message.setMessageType(dto.getMessageType());
        message.setContent(dto.getContent());
        message.setSequence(conversationService.nextSequence(dto.getConversationId()));

        Instant now = Instant.now();
        message.setTimestamp(now);

        Message savedMessage = messageRepository.save(message);

        conversationService.updateLastInteraction(conversation, now);
        messageBroadcastService.broadcastInline(savedMessage);

        return toResponse(savedMessage);
    }

    public MessageResponseDTO ingestChatbotMessage(ChatbotMessageRequestDTO dto) {
        Conversation conversation = conversationService.requireEntityById(dto.getConversationId());
        if (conversation.getConversationType() != ConversationType.CHAT_BOT) {
            throw new InvalidFieldException(
                    "Mensagens do chatbot só podem ser publicadas em conversas do tipo CHAT_BOT.");
        }
        conversationService.requireActive(conversation);

        Message message = new Message();
        message.setConversationId(dto.getConversationId());
        message.setRole(ROLE_ASSISTANT);
        message.setMessageType(MessageType.CHATBOT_TO_USER);
        message.setContent(dto.getContent());
        message.setMetadata(dto.getMetadata());
        message.setSequence(conversationService.nextSequence(dto.getConversationId()));

        Instant now = Instant.now();
        message.setTimestamp(now);

        Message savedMessage = messageRepository.save(message);
        conversationService.updateLastInteraction(conversation, now);
        messageBroadcastService.broadcastInline(savedMessage);

        return toResponse(savedMessage);
    }

    public List<MessageResponseDTO> getMessagesByConversationId(String conversationId) {
        return getMessagesByConversationId(conversationId, null, null);
    }

    public List<MessageResponseDTO> getMessagesByConversationId(String conversationId, Integer sinceSequence) {
        return getMessagesByConversationId(conversationId, sinceSequence, null);
    }


    public List<MessageResponseDTO> getMessagesByConversationId(String conversationId, Integer sinceSequence, Integer limit) {
        Conversation conversation = conversationService.requireEntityById(conversationId);
        conversationService.requireParticipant(conversation);

        Limit pageLimit = Limit.of(clampMessagePageSize(limit));
        List<Message> messages = sinceSequence == null
                ? messageRepository.findByConversationIdOrderBySequenceAscTimestampAsc(conversationId, pageLimit)
                : messageRepository.findByConversationIdAndSequenceGreaterThanOrderBySequenceAscTimestampAsc(
                        conversationId, sinceSequence, pageLimit);

        return messages.stream()
                .map(this::toResponse)
                .toList();
    }

    private int clampMessagePageSize(Integer requested) {
        if (requested == null) {
            return DEFAULT_MESSAGE_PAGE_SIZE;
        }
        return Math.max(1, Math.min(requested, MAX_MESSAGE_PAGE_SIZE));
    }

    /**
     * Garante que o {@code messageType} enviado combina com o tipo da conversa:
     * <ul>
     *   <li>{@code GROUP} -> {@code USER_TO_GROUP}</li>
     *   <li>{@code DIRECT} -> {@code USER_TO_USER}</li>
     *   <li>{@code CHAT_BOT} -> {@code USER_TO_CHATBOT}</li>
     * </ul>
     */
    private void requireMessageTypeMatchesConversation(MessageType messageType, ConversationType conversationType) {
        Set<MessageType> allowed = switch (conversationType) {
            case GROUP -> Set.of(MessageType.USER_TO_GROUP);
            case DIRECT -> Set.of(MessageType.USER_TO_USER);
            case CHAT_BOT -> Set.of(MessageType.USER_TO_CHATBOT);
        };
        if (!allowed.contains(messageType)) {
            throw new InvalidFieldException(
                    "messageType " + messageType + " não é válido para uma conversa do tipo " + conversationType + ".");
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
