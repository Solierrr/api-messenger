package com.solaria.messenger.dto.request;

import java.util.Set;
import java.util.UUID;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/**
 * Cria a comunidade de um projeto
 * Quem cria vira {@code OWNER}
 * Os {@code memberIds} opcionais entram como {@code MEMBER}
 */
@Getter
@Setter
public class CommunityRequestDTO {

    @NotNull(message = "O identificador do projeto é obrigatório")
    private UUID projectId;

    private Set<@NotNull(message = "memberId inválido") UUID> memberIds;
}
