package com.solaria.messenger.core.contract.request;

import java.math.BigDecimal;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;


/**
 * DTO de requisição para atualização de uma avaliação existente.
 *
 * <p>Usado no endpoint {@code PATCH /messaging/ratings/{id}}. Carrega a nota (0.0 a 5.0) e o
 * comentário (até 1000 caracteres), ambos opcionais na atualização.</p>
 */
@Setter
@Getter
public class RatingUpdateRequestDTO {

    @Size(max = 1000, message = "O comentário deve ter no máximo 1000 caracteres")
    private String comment;

    @DecimalMin(value = "0.0", message = "A nota deve ser maior ou igual a 0.0")
    @DecimalMax(value = "5.0", message = "A nota deve ser menor ou igual a 5.0")
    private BigDecimal rating;

}
