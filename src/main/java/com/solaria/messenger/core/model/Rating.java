package com.solaria.messenger.core.model;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import com.solaria.messenger.core.model.enums.RatingStatus;
import com.solaria.messenger.core.model.enums.RatingType;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;


/**
 * avaliação feita por um usuário sobre um avaliado(profissional|produto)
 *
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "rating")
public class Rating {

    @Id
    private String id;

    @Indexed
    @Field("evaluator_id")
    private UUID evaluatorId;

    @Indexed
    @Field("evaluated_id")
    private UUID evaluatedId;

    @Field("type_rate")
    private RatingType typeRate;

    private String comment;

    private BigDecimal rating;

    private RatingStatus status;

    private Instant timestamp;
}
