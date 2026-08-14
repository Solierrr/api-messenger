package com.solaria.messenger.service;

import java.time.Instant;
import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import com.solaria.messenger.model.Conversation;
import com.solaria.messenger.repository.ConversationRepository;

@Service
public class ConversationService {

    private final ConversationRepository conversationRepository;

    public ConversationService(ConversationRepository conversationRepository) {
        this.conversationRepository = conversationRepository;
    }

    public Conversation createConversation(Conversation conversation) {
        Instant now = Instant.now();
        conversation.setStartedAt(now);
        conversation.setLastInteractionAt(now);
        if (conversation.getStatus() == null || conversation.getStatus().isBlank()) {
            conversation.setStatus("active");
        }
        return conversationRepository.save(conversation);
    }

    public Conversation getConversationById(String id) {
        return conversationRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Conversation not found"));
    }

    public void updateLastInteraction(Conversation conversation, Instant timestamp) {
        conversation.setLastInteractionAt(timestamp);
        conversationRepository.save(conversation);
    }

    public List<Conversation> getConversationsByUserId(Long userId) {
        return conversationRepository.findByUserId(userId);
    }
}
