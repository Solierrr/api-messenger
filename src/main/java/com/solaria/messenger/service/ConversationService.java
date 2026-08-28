package com.solaria.messenger.service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.solaria.messenger.dto.request.ChatbotConversationRequestDTO;
import com.solaria.messenger.dto.request.UserConversationRequestDTO;
import com.solaria.messenger.dto.response.ConversationResponseDTO;
import com.solaria.messenger.exception.ResourceNotFoundException;
import com.solaria.messenger.model.Conversation;
import com.solaria.messenger.model.enums.ConversationStatus;
import com.solaria.messenger.model.enums.ConversationType;
import com.solaria.messenger.model.enums.Environment;
import com.solaria.messenger.repository.ConversationRepository;
import com.solaria.messenger.security.rbac.RbacAuthorizationService;

@Service
public class ConversationService {

    private final ConversationRepository conversationRepository;
    private final RbacAuthorizationService rbac;

    public ConversationService(ConversationRepository conversationRepository, RbacAuthorizationService rbac) {
        this.conversationRepository = conversationRepository;
        this.rbac = rbac;
    }

    public ConversationResponseDTO createUserConversation(UserConversationRequestDTO dto) {
        Conversation conversation = new Conversation();
        conversation.setSenderId(rbac.currentUserId());
        conversation.setReceiverId(dto.getReceiverId());
        conversation.setConversationType(ConversationType.USER_CONVERSATION);
        conversation.setStatus(ConversationStatus.ACTIVE);

        Instant now = Instant.now();
        conversation.setStartedAt(now);
        conversation.setLastInteractionAt(now);

        return toResponse(conversationRepository.save(conversation));
    }


    public ConversationResponseDTO createChatbotConversation(ChatbotConversationRequestDTO dto) {
        Conversation conversation = new Conversation();
        conversation.setReceiverId(rbac.currentUserId());
        conversation.setConversationType(ConversationType.CHAT_BOT);
        conversation.setEnvironment(dto.getEnvironment());
        conversation.setUserType(dto.getUserType());
        conversation.setUserDetails(dto.getUserDetails());
        conversation.setStatus(ConversationStatus.ACTIVE);

        Instant now = Instant.now();
        conversation.setStartedAt(now);
        conversation.setLastInteractionAt(now);

        return toResponse(conversationRepository.save(conversation));
    }

    public ConversationResponseDTO findById(String id) {
        Conversation conversation = requireEntityById(id);
        requireParticipant(conversation);
        return toResponse(conversation);
    }

    public List<ConversationResponseDTO> findMine() {
        UUID currentUserId = rbac.currentUserId();
        return conversationRepository.findByReceiverIdOrSenderId(currentUserId, currentUserId)
                .stream()
                .map(this::toResponse)
                .toList();
    }


    public Conversation requireEntityById(String id) {
        return conversationRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Conversa não encontrada com id: " + id));
    }


    public void requireParticipant(Conversation conversation) {
        if (conversation.getConversationType() == ConversationType.USER_CONVERSATION) {
            rbac.requireParticipant(conversation.getSenderId(), conversation.getReceiverId());
        } else {
            rbac.requireParticipant(conversation.getReceiverId());
        }
    }


    public void updateLastInteraction(Conversation conversation, Instant timestamp) {
        conversation.setLastInteractionAt(timestamp);
        conversationRepository.save(conversation);
    }

    private ConversationResponseDTO toResponse(Conversation conversation) {
        return ConversationResponseDTO.builder()
                .id(conversation.getId())
                .senderId(conversation.getSenderId())
                .receiverId(conversation.getReceiverId())
                .conversationType(conversation.getConversationType())
                .environment(conversation.getEnvironment())
                .userType(conversation.getUserType())
                .userDetails(conversation.getUserDetails())
                .status(conversation.getStatus())
                .startedAt(conversation.getStartedAt())
                .lastInteractionAt(conversation.getLastInteractionAt())
                .build();
    }
}
