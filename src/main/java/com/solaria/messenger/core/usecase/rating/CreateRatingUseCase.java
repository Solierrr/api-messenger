package com.solaria.messenger.core.usecase.rating;

import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.solaria.messenger.core.contract.request.RatingRequestDTO;
import com.solaria.messenger.core.contract.response.RatingResponseDTO;
import com.solaria.messenger.core.exception.BusinessRuleException;
import com.solaria.messenger.core.model.Rating;
import com.solaria.messenger.core.model.enums.RatingStatus;
import com.solaria.messenger.core.repository.RatingRepository;
import com.solaria.messenger.core.security.rbac.RbacAuthorizationService;

import static com.solaria.messenger.core.mapper.RatingMapper.toResponse;

@Service
public class CreateRatingUseCase {

    private final RatingRepository ratingRepository;
    private final RbacAuthorizationService rbac;

    public CreateRatingUseCase(RatingRepository ratingRepository, RbacAuthorizationService rbac) {
        this.ratingRepository = ratingRepository;
        this.rbac = rbac;
    }

    /** Cria uma avaliação ativa; ninguém pode avaliar a si mesmo. */
    public RatingResponseDTO execute(RatingRequestDTO dto) {
        UUID evaluatorId = rbac.currentUserId();
        if (evaluatorId.equals(dto.getEvaluatedId())) {
            throw new BusinessRuleException("Não é permitido avaliar a si mesmo.");
        }

        Rating rating = new Rating();
        rating.setEvaluatorId(evaluatorId);
        rating.setEvaluatedId(dto.getEvaluatedId());
        rating.setTypeRate(dto.getTypeRate());
        rating.setComment(dto.getComment());
        rating.setRating(dto.getRating());
        rating.setStatus(RatingStatus.ACTIVE);
        rating.setTimestamp(Instant.now());

        return toResponse(ratingRepository.save(rating));
    }
}
