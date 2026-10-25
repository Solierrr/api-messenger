package com.solaria.messenger.core.contract.request;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;

import lombok.Getter;
import lombok.Setter;

/**
 * Atualiza o nome e a descrição da comunidade; campo ausente fica como está
 * O status não entra aqui: ele segue o projeto na api-core
 */
@Getter
@Setter
public class CommunityUpdateRequestDTO {

    @Schema(description = "Novo nome; ausente mantém o atual, vazio é INVALID_FIELD.")
    @Size(max = 120, message = "O nome deve ter no máximo 120 caracteres")
    private String name;

    @Schema(description = "Nova descrição; ausente mantém a atual, vazia apaga.")
    @Size(max = 1000, message = "A descrição deve ter no máximo 1000 caracteres")
    private String description;
}
