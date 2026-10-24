package com.solaria.messenger.core.contract.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * DTO de requisição para edição do conteúdo de uma mensagem.
 *
 * <p>Usado no endpoint {@code PATCH /messaging/messages/{id}}. O conteúdo é obrigatório e limitado
 * a 8000 caracteres.</p>
 */
@Getter
@Setter
public class MessageEditRequestDTO {

    @NotBlank(message = "content é obrigatório")
    @Size(max = 8000, message = "content deve ter no máximo 8000 caracteres")
    private String content;
}
