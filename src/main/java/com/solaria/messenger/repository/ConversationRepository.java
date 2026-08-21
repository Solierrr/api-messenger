package com.solaria.messenger.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.solaria.messenger.model.Conversation;

public interface ConversationRepository extends MongoRepository<Conversation, String> {

    List<Conversation> findByReceiverIdOrSenderId(UUID receiverId, UUID senderId);
}
