package com.solaria.messenger.core.contract.request;

import com.solaria.messenger.core.model.enums.AttachmentType;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Getter;
import lombok.Setter;

/**
 * Pedido de permissão para depositar um arquivo, feito antes de enviar a mensagem que o cita.
 *
 * <p>Usado em {@code POST /messaging/media}. O nome do arquivo não vem aqui: ele só viaja na descrição do anexo, no
 * envio da mensagem.</p>
 */
@Getter
@Setter
public class MediaUploadRequestDTO {

    @NotBlank(message = "conversationId é obrigatório")
    private String conversationId;

    @NotNull(message = "type é obrigatório")
    private AttachmentType type;

    @NotBlank(message = "mimeType é obrigatório")
    private String mimeType;

    @NotNull(message = "sizeBytes é obrigatório")
    @Positive(message = "sizeBytes deve ser maior que zero")
    private Long sizeBytes;
}
