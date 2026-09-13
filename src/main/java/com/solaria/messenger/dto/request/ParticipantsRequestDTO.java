package com.solaria.messenger.dto.request;

import java.util.Set;
import java.util.UUID;

import jakarta.validation.constraints.NotEmpty;
import lombok.Getter;
import lombok.Setter;

/**
 * Adiciona participantes a uma conversa em grupo existente
 */
@Getter
@Setter
public class ParticipantsRequestDTO {

    @NotEmpty(message = "Informe ao menos um usuário")
    private Set<@jakarta.validation.constraints.NotNull(message = "userId inválido") UUID> userIds;
}
