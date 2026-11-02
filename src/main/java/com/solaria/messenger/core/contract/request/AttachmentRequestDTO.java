package com.solaria.messenger.core.contract.request;

import com.solaria.messenger.core.model.enums.AttachmentType;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * Anexo de uma mensagem enviada.
 *
 * <p>Em {@code IMAGE}, {@code VIDEO}, {@code AUDIO} e {@code FILE} o arquivo já foi depositado por
 * {@code POST /messaging/media} e aqui vai só a descrição, com o {@code mediaId}. Em {@code LOCATION} vão as
 * coordenadas. O {@code clientPreview} é um texto opaco que o servidor guarda sem interpretar.</p>
 */
@Getter
@Setter
public class AttachmentRequestDTO {

    @NotNull(message = "type é obrigatório")
    private AttachmentType type;

    private String mediaId;

    private String mimeType;

    @Positive(message = "sizeBytes deve ser maior que zero")
    private Long sizeBytes;

    @Size(max = 255, message = "name deve ter no máximo 255 caracteres")
    private String name;

    @PositiveOrZero(message = "durationMs não pode ser negativo")
    private Long durationMs;

    @Positive(message = "width deve ser maior que zero")
    private Integer width;

    @Positive(message = "height deve ser maior que zero")
    private Integer height;

    @Size(max = 2048, message = "clientPreview deve ter no máximo 2048 caracteres")
    private String clientPreview;

    @DecimalMin(value = "-90.0", message = "latitude deve estar entre -90 e 90")
    @DecimalMax(value = "90.0", message = "latitude deve estar entre -90 e 90")
    private Double latitude;

    @DecimalMin(value = "-180.0", message = "longitude deve estar entre -180 e 180")
    @DecimalMax(value = "180.0", message = "longitude deve estar entre -180 e 180")
    private Double longitude;

    @Size(max = 255, message = "label deve ter no máximo 255 caracteres")
    private String label;
}
