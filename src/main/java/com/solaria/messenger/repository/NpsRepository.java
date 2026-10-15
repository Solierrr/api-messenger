package com.solaria.messenger.repository;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.solaria.messenger.model.Nps;

public interface NpsRepository extends MongoRepository<Nps, String> {

    boolean existsByConversationId(String conversationId);
}
