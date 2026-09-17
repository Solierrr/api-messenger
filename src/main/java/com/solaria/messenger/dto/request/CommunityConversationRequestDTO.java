package com.solaria.messenger.dto.request;

import java.util.Set;
import java.util.UUID;

import com.solaria.messenger.model.enums.Environment;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * Inicia uma conversa em grupo dentro de uma comunidade de projeto. 
 * Se {@code participantIds} for vazio, entram todos os membros da comunidade
 */
@Getter
@Setter
public class CommunityConversationRequestDTO {

    @NotBlank(message = "O título do grupo é obrigatório")
    @Size(max = 120, message = "O título deve ter no máximo 120 caracteres")
    private String title;

    private Set<@NotNull(message = "participantId inválido") UUID> participantIds;

    private Environment environment;
}
