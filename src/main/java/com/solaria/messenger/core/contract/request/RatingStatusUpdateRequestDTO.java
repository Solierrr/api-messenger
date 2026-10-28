package com.solaria.messenger.core.contract.request;

import com.solaria.messenger.core.model.enums.RatingStatus;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;


/**
 * DTO de requisição para alteração do status de uma avaliação.
 *
 * <p>Usado no endpoint {@code PATCH /messaging/ratings/{id}/status}. O status é obrigatório.</p>
 */
@Setter
@Getter
public class RatingStatusUpdateRequestDTO {

    @NotNull(message = "O status é obrigatório")
    private RatingStatus status;

}
