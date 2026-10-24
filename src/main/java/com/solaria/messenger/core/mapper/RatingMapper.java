package com.solaria.messenger.core.mapper;

import com.solaria.messenger.core.contract.response.RatingResponseDTO;
import com.solaria.messenger.core.model.Rating;

/** Mapeamento único da entidade persistida para o contrato público, sem consultas ou autorização. */
public final class RatingMapper {
    private RatingMapper() { }

    /** Converte a avaliação persistida no contrato de resposta. */
    public static RatingResponseDTO toResponse(Rating rating) {
        return RatingResponseDTO.builder()
                .id(rating.getId())
                .evaluatorId(rating.getEvaluatorId())
                .evaluatedId(rating.getEvaluatedId())
                .typeRate(rating.getTypeRate())
                .comment(rating.getComment())
                .rating(rating.getRating())
                .status(rating.getStatus())
                .timestamp(rating.getTimestamp())
                .build();
    }
}
