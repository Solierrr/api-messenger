package com.solaria.messenger.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.solaria.messenger.model.Message;

public interface MessageRepository extends MongoRepository<Message, String> {

    List<Message> findByConversationIdOrderByTimestampAsc(String conversationId);

    Optional<Message> findByConversationIdAndClientMessageId(String conversationId, String clientMessageId);

    List<Message> findByConversationIdOrderBySequenceAsc(String conversationId);

    List<Message> findByConversationIdAndSequenceGreaterThanOrderBySequenceAsc(String conversationId, int sequence);

    List<Message> findByBroadcastedAtIsNull();
}
