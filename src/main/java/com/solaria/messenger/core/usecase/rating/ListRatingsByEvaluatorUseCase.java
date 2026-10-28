package com.solaria.messenger.core.usecase.rating;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.solaria.messenger.core.contract.response.RatingResponseDTO;
import com.solaria.messenger.core.mapper.RatingMapper;
import com.solaria.messenger.core.repository.RatingRepository;

/**
 * Caso de uso que lista as avaliações feitas por um avaliador.
 *
 * <p>
 * Não filtra por status: traz todas as avaliações gravadas pelo avaliador.
 * </p>
 *
 * <p>
 * <b>Depende de:</b>
 * </p>
 * <ul>
 *     <li>{@link RatingRepository} -> busca as avaliações pelo avaliador.</li>
 * </ul>
 *
 * <p>
 * Principal operação disponibilizada:
 * </p>
 * <ul>
 *     <li>{@link #execute(UUID)} -> lista as avaliações feitas.</li>
 * </ul>
 */
@Service
public class ListRatingsByEvaluatorUseCase {

    private final RatingRepository ratingRepository;

    public ListRatingsByEvaluatorUseCase(RatingRepository ratingRepository) {
        this.ratingRepository = ratingRepository;
    }

    /**
     * Lista as avaliações feitas pelo avaliador.
     *
     * @param evaluatorId identificador do avaliador.
     * @return avaliações feitas, na ordem devolvida pelo repository.
     */
    public List<RatingResponseDTO> execute(UUID evaluatorId) {
        return ratingRepository.findByEvaluatorId(evaluatorId).stream().map(RatingMapper::toResponse).toList();
    }
}
