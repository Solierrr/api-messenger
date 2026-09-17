package com.solaria.messenger.dto.request;

import java.util.UUID;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class UserConversationRequestDTO {

    @NotNull(message = "O identificador do destinatário é obrigatório")
    private UUID receiverId;
}
