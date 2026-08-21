package com.solaria.messenger.repository;

import java.util.List;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.solaria.messenger.model.LlmObservability;

public interface LlmObservabilityRepository extends MongoRepository<LlmObservability, String> {

    List<LlmObservability> findByConversationId(String conversationId);

    List<LlmObservability> findByNode(String node);

    List<LlmObservability> findByStatus(String status);
}
