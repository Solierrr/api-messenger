package com.solaria.messenger.core.contract.request;

import com.solaria.messenger.core.model.enums.CommunityStatus;

import io.swagger.v3.oas.annotations.media.Schema;

import lombok.Getter;
import lombok.Setter;

/**
 * Atualiza o status da comunidade
 */
@Getter
@Setter
public class CommunityUpdateRequestDTO {

    @Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "Status obrigatório; ausente ou nulo é INVALID_FIELD.")
    private CommunityStatus status;
}
