package com.solaria.messenger.dto.request;

import java.util.UUID;

import com.solaria.messenger.model.enums.Environment;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/**
 * Inicia (ou reabre) uma conversa entre duas pessoas
 * O outro participante é {@code recipientId}
 * o iniciador é sempre o usuário autenticado do JWT)
 */
@Getter
@Setter
public class DirectConversationRequestDTO {

    @NotNull(message = "O identificador do destinatário é obrigatório")
    private UUID recipientId;

    private Environment environment;
}
