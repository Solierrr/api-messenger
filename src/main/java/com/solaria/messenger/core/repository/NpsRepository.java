package com.solaria.messenger.core.repository;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.solaria.messenger.core.model.Nps;

/**
 * Repositório da entidade {@link Nps}, usado para garantir uma única resposta de
 * NPS por conversa.
 *
 * <p>Principais operações disponibilizadas:</p>
 * <ul>
 *     <li>{@link #existsByConversationId(String)} -> indica se a conversa já recebeu NPS.</li>
 * </ul>
 */
public interface NpsRepository extends MongoRepository<Nps, String> {

    /** Indica se a conversa já recebeu NPS. */
    boolean existsByConversationId(String conversationId);
}
