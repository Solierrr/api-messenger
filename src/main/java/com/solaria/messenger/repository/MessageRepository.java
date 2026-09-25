package com.solaria.messenger.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Limit;
import org.springframework.data.mongodb.repository.MongoRepository;

import com.solaria.messenger.model.Message;

public interface MessageRepository extends MongoRepository<Message, String> {

    Optional<Message> findByConversationIdAndSenderIdAndClientMessageId(
            String conversationId, UUID senderId, String clientMessageId);


    List<Message> findByConversationIdOrderBySequenceAscTimestampAsc(String conversationId, Limit limit);

    List<Message> findByConversationIdAndSequenceGreaterThanOrderBySequenceAscTimestampAsc(
            String conversationId, int sequence, Limit limit);
}
