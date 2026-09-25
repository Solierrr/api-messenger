package com.solaria.messenger.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.solaria.messenger.model.Conversation;

public interface ConversationRepository extends MongoRepository<Conversation, String> {

    /** Conversas em que o user participa */
    List<Conversation> findByParticipantIdsContainingOrderByLastInteractionAtDesc(UUID userId);

    List<Conversation> findByCommunityIdAndParticipantIdsContainingOrderByLastInteractionAtDesc(
            String communityId, UUID userId);
}
