package com.solaria.messenger.core.usecase.rating;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.solaria.messenger.core.contract.response.RatingResponseDTO;
import com.solaria.messenger.core.mapper.RatingMapper;
import com.solaria.messenger.core.repository.RatingRepository;

/**
 * Caso de uso que lista as avaliações recebidas por um user.
 *
 * <p>
 * Não filtra por status: traz todas as avaliações gravadas para o avaliado.
 * </p>
 *
 * <p>
 * <b>Depende de:</b>
 * </p>
 * <ul>
 *     <li>{@link RatingRepository} -> busca as avaliações pelo avaliado.</li>
 * </ul>
 *
 * <p>
 * Principal operação disponibilizada:
 * </p>
 * <ul>
 *     <li>{@link #execute(UUID)} -> lista as avaliações recebidas.</li>
 * </ul>
 */
@Service
public class ListRatingsByEvaluatedUseCase {

    private final RatingRepository ratingRepository;

    public ListRatingsByEvaluatedUseCase(RatingRepository ratingRepository) {
        this.ratingRepository = ratingRepository;
    }

    /**
     * Lista as avaliações recebidas pelo avaliado.
     *
     * @param evaluatedId identificador do avaliado.
     * @return avaliações recebidas, na ordem devolvida pelo repository.
     */
    public List<RatingResponseDTO> execute(UUID evaluatedId) {
        return ratingRepository.findByEvaluatedId(evaluatedId).stream().map(RatingMapper::toResponse).toList();
    }
}
