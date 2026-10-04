package com.solaria.messenger.core.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.solaria.messenger.core.model.Rating;

/**
 * Repositório Spring Data MongoDB da entidade {@link Rating}.
 *
 * <p>Principais consultas disponibilizadas:</p>
 * <ul>
 *     <li>{@link #findByEvaluatedId(UUID)} -> avaliações recebidas por um avaliado.</li>
 *     <li>{@link #findByEvaluatorId(UUID)} -> avaliações feitas por um avaliador.</li>
 * </ul>
 */
public interface RatingRepository extends MongoRepository<Rating, String> {

    /** Avaliações recebidas pelo avaliado. */
    List<Rating> findByEvaluatedId(UUID evaluatedId);

    /** Avaliações feitas pelo avaliador. */
    List<Rating> findByEvaluatorId(UUID evaluatorId);
}
