package com.solaria.messenger.dto.request;

import java.util.Set;
import java.util.UUID;

import com.solaria.messenger.model.enums.Environment;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * Inicia uma conversa em grupo 
 * O criador é adicionado automaticamente aos participantes
 */
@Getter
@Setter
public class GroupConversationRequestDTO {

    @NotBlank(message = "O título do grupo é obrigatório")
    @Size(max = 120, message = "O título deve ter no máximo 120 caracteres")
    private String title;

    @NotEmpty(message = "Informe ao menos um outro participante")
    private Set<@jakarta.validation.constraints.NotNull(message = "participantId inválido") UUID> participantIds;

    private Environment environment;
}
