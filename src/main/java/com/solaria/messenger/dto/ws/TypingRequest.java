package com.solaria.messenger.dto.ws;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;

/** Payload minimo de {@code /app/typing.started} e {@code /app/typing.stopped} | usuario digintando... */
@Getter
@Setter
public class TypingRequest {

    @NotBlank(message = "conversationId é obrigatório")
    private String conversationId;
}
