package com.solaria.messenger.dto.request;

import java.math.BigDecimal;
import java.util.UUID;

import com.solaria.messenger.model.enums.RatingType;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
public class RatingRequestDTO {

    @NotNull(message = "O identificador do avaliado é obrigatório")
    private UUID evaluatedId;

    @NotNull(message = "O tipo da avaliação é obrigatório")
    private RatingType typeRate;

    @Size(max = 1000, message = "O comentário deve ter no máximo 1000 caracteres")
    private String comment;

    @NotNull(message = "A nota é obrigatória")
    @DecimalMin(value = "0.0", message = "A nota deve ser maior ou igual a 0.0")
    @DecimalMax(value = "5.0", message = "A nota deve ser menor ou igual a 5.0")
    private BigDecimal rating;

}
