package com.solaria.messenger.dto.response;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import com.solaria.messenger.model.enums.RatingStatus;
import com.solaria.messenger.model.enums.RatingType;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

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
