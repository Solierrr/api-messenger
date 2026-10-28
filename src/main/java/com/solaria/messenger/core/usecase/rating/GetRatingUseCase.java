package com.solaria.messenger.core.usecase.rating;

import org.springframework.stereotype.Service;

import com.solaria.messenger.core.contract.response.RatingResponseDTO;
import com.solaria.messenger.core.helper.LookupHelper;

import static com.solaria.messenger.core.mapper.RatingMapper.toResponse;

@Service
public class GetRatingUseCase {

    private final LookupHelper lookup;

    public GetRatingUseCase(LookupHelper lookup) {
        this.lookup = lookup;
    }

    /** Busca uma avaliação pelo ID. */
    public RatingResponseDTO execute(String id) {
        return toResponse(lookup.requireRating(id));
    }
}
