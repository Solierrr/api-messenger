package com.solaria.messenger.service;

import java.time.Instant;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import com.solaria.messenger.model.Conversation;
import com.solaria.messenger.model.Message;
import com.solaria.messenger.repository.MessageRepository;

@Service
public class MessageService {

    private final MessageRepository messageRepository;
    private final ConversationService conversationService;

    public MessageService(MessageRepository messageRepository, ConversationService conversationService) {
        this.messageRepository = messageRepository;
        this.conversationService = conversationService;
    }

    public Message sendMessage(Message message) {
        Conversation conversation = conversationService.getConversationById(message.getConversationId());
        Instant now = Instant.now();
        message.setTimestamp(now);
        Message savedMessage = messageRepository.save(message);
        conversationService.updateLastInteraction(conversation, now);
        return savedMessage;
    }

    public Page<Message> getMessagesByConversationId(String conversationId, Pageable pageable) {
        return messageRepository.findByConversationIdOrderByTimestampAsc(conversationId, pageable);
    }
}
