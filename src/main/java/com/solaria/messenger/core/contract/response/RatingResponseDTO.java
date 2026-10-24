package com.solaria.messenger.core.contract.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.solaria.messenger.core.model.enums.RatingStatus;
import com.solaria.messenger.core.model.enums.RatingType;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * DTO de resposta com os dados de uma avaliação.
 *
 * <p>Retornado pelos endpoints de {@code /messaging/ratings}. Inclui avaliador, avaliado, tipo,
 * nota, comentário e status.</p>
 */
@Setter
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RatingResponseDTO {

    private String id;
    private UUID evaluatorId;
    private UUID evaluatedId;
    private RatingType typeRate;
    private String comment;
    private BigDecimal rating;
    private RatingStatus status;
    private Instant timestamp;

}
