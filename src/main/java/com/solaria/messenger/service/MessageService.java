package com.solaria.messenger.service;

import java.time.Instant;
import java.util.List;
import java.util.Set;

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

    private final MessageRepository messageRepository;
    private final ConversationService conversationService;
    private final RbacAuthorizationService rbac;

    public MessageService(MessageRepository messageRepository,
            ConversationService conversationService,
            RbacAuthorizationService rbac) {
        this.messageRepository = messageRepository;
        this.conversationService = conversationService;
        this.rbac = rbac;
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
        message.setRole(dto.getRole());
        message.setMessageType(dto.getMessageType());
        message.setEnvironment(dto.getEnvironment());
        message.setContent(dto.getContent());

        Instant now = Instant.now();
        message.setTimestamp(now);

        Message savedMessage = messageRepository.save(message);
        conversationService.updateLastInteraction(conversation, now);

        return toResponse(savedMessage);
    }

    public MessageResponseDTO ingestChatbotMessage(ChatbotMessageRequestDTO dto) {
        Conversation conversation = conversationService.requireEntityById(dto.getConversationId());

        Message message = new Message();
        message.setConversationId(dto.getConversationId());
        message.setRole("assistant");
        message.setMessageType(MessageType.CHATBOT_TO_USER);
        message.setEnvironment(dto.getEnvironment());
        message.setContent(dto.getContent());
        message.setMetadata(dto.getMetadata());

        Instant now = Instant.now();
        message.setTimestamp(now);

        Message savedMessage = messageRepository.save(message);
        conversationService.updateLastInteraction(conversation, now);

        return toResponse(savedMessage);
    }

    public List<MessageResponseDTO> getMessagesByConversationId(String conversationId) {
        return getMessagesByConversationId(conversationId, null);
    }

    public List<MessageResponseDTO> getMessagesByConversationId(String conversationId, Integer sinceSequence) {
        Conversation conversation = conversationService.requireEntityById(conversationId);
        conversationService.requireParticipant(conversation);

        List<Message> messages = sinceSequence == null
                ? messageRepository.findByConversationIdOrderBySequenceAsc(conversationId)
                : messageRepository.findByConversationIdAndSequenceGreaterThanOrderBySequenceAsc(
                        conversationId,
                        sinceSequence);

        return messages
                .stream()
                .map(this::toResponse)
                .toList();
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
                .environment(message.getEnvironment())
                .content(message.getContent())
                .metadata(message.getMetadata())
                .timestamp(message.getTimestamp())
                .build();
    }
}
