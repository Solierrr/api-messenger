package com.solaria.messenger.core.contract.request;

import com.solaria.messenger.core.model.enums.MessageType;
import io.swagger.v3.oas.annotations.media.Schema;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * DTO de requisição para envio de uma mensagem em uma conversa.
 *
 * <p>Usado no endpoint {@code POST /messaging/messages}. Carrega a conversa de destino, o tipo e o
 * conteúdo da mensagem, além do {@code clientMessageId} opcional de correlação da tentativa de envio.</p>
 */
@Getter
@Setter
public class MessageRequestDTO {

    @NotBlank(message = "conversationId é obrigatório")
    private String conversationId;

    /** Correlação opcional da tentativa; repetir o valor não reaproveita uma mensagem. */
    @Schema(nullable = true, description = "UUID opcional de correlação, sem deduplicação ou unicidade")
    @Pattern(regexp = "^[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}$",
            message = "clientMessageId deve ser um UUID canônico")
    private String clientMessageId;

    @NotNull(message = "messageType é obrigatório")
    private MessageType messageType;


    @Size(max = 32, message = "role deve ter no máximo 32 caracteres")
    private String role;

    @NotBlank(message = "content é obrigatório")
    @Size(max = 8000, message = "content deve ter no máximo 8000 caracteres")
    private String content;
}
