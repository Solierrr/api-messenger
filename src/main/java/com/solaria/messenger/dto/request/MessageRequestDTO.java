package com.solaria.messenger.dto.request;

import com.solaria.messenger.model.enums.MessageType;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class MessageRequestDTO {

    @NotBlank(message = "conversationId é obrigatório")
    private String conversationId;

    @NotNull(message = "messageType é obrigatório")
    private MessageType messageType;

    @NotBlank(message = "role é obrigatório")
    @Size(max = 32, message = "role deve ter no máximo 32 caracteres")
    private String role;

    @NotBlank(message = "content é obrigatório")
    @Size(max = 8000, message = "content deve ter no máximo 8000 caracteres")
    private String content;
}
