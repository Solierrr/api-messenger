package com.solaria.messenger.core.usecase.rating;

import org.springframework.stereotype.Service;

import com.solaria.messenger.core.contract.request.RatingStatusUpdateRequestDTO;
import com.solaria.messenger.core.contract.response.RatingResponseDTO;
import com.solaria.messenger.core.helper.LookupHelper;
import com.solaria.messenger.core.model.Rating;
import com.solaria.messenger.core.repository.RatingRepository;
import com.solaria.messenger.core.security.rbac.RbacAuthorizationService;

import static com.solaria.messenger.core.mapper.RatingMapper.toResponse;

@Service
public class UpdateRatingStatusUseCase {

    private final RatingRepository ratingRepository;
    private final RbacAuthorizationService rbac;
    private final LookupHelper lookup;

    public UpdateRatingStatusUseCase(RatingRepository ratingRepository, RbacAuthorizationService rbac,
            LookupHelper lookup) {
        this.ratingRepository = ratingRepository;
        this.rbac = rbac;
        this.lookup = lookup;
    }

    /** Altera o status de uma avaliação do próprio avaliador. */
    public RatingResponseDTO execute(String id, RatingStatusUpdateRequestDTO dto) {
        Rating rating = lookup.requireRating(id);
        rbac.requireOwnResource(rating.getEvaluatorId());

        rating.setStatus(dto.getStatus());

        return toResponse(ratingRepository.save(rating));
    }
}
