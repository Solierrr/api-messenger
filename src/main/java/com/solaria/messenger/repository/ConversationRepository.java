package com.solaria.messenger.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.solaria.messenger.model.Conversation;
import com.solaria.messenger.model.enums.ConversationType;

public interface ConversationRepository extends MongoRepository<Conversation, String> {

    /** Conversas em que o user participa */
    List<Conversation> findByParticipantIdsContainingOrderByLastInteractionAtDesc(UUID userId);

    /** Conversas de um tipo em que o usuário é participante */
    List<Conversation> findByConversationTypeAndParticipantIdsContaining(ConversationType conversationType, UUID userId);

    /** Todas as conversas de uma comunidade */
    List<Conversation> findByCommunityIdOrderByLastInteractionAtDesc(String communityId);
}
