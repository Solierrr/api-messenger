package com.solaria.messenger.repository;

import java.util.List;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.solaria.messenger.model.Message;

public interface MessageRepository extends MongoRepository<Message, String> {

    List<Message> findByConversationIdOrderByTimestampAsc(String conversationId);
}
