package com.solaria.messenger.dto.request;

import com.solaria.messenger.model.enums.RatingStatus;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;


@Setter
@Getter
public class RatingStatusUpdateRequestDTO {

    @NotNull(message = "O status é obrigatório")
    private RatingStatus status;

}
