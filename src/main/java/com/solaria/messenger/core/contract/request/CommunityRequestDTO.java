package com.solaria.messenger.core.contract.request;

import java.util.Set;
import java.util.UUID;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/**
 * Cria a comunidade de um projeto
 * Os papéis e envolvidos são derivados da api-core.
 * {@code memberIds} não concede participação nem altera papéis.
 */
@Getter
@Setter
public class CommunityRequestDTO {

    @NotNull(message = "O identificador do projeto é obrigatório")
    private UUID projectId;

    private Set<@NotNull(message = "memberId inválido") UUID> memberIds;
}
