package com.solaria.messenger.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.solaria.messenger.model.Conversation;

public interface ConversationRepository extends MongoRepository<Conversation, String> {

    List<Conversation> findByUserId(Long userId);

    Optional<Conversation> findBySessionId(String sessionId);
}
