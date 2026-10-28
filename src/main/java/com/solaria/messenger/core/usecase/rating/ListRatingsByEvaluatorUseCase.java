package com.solaria.messenger.core.usecase.rating;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.solaria.messenger.core.contract.response.RatingResponseDTO;
import com.solaria.messenger.core.mapper.RatingMapper;
import com.solaria.messenger.core.repository.RatingRepository;

@Service
public class ListRatingsByEvaluatorUseCase {

    private final RatingRepository ratingRepository;

    public ListRatingsByEvaluatorUseCase(RatingRepository ratingRepository) {
        this.ratingRepository = ratingRepository;
    }

    /** Lista as avaliações feitas por um avaliador. */
    public List<RatingResponseDTO> execute(UUID evaluatorId) {
        return ratingRepository.findByEvaluatorId(evaluatorId).stream().map(RatingMapper::toResponse).toList();
    }
}
