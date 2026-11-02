package com.solaria.messenger.core.model;

import java.time.Instant;

import org.springframework.data.mongodb.core.mapping.Field;

import com.solaria.messenger.core.model.enums.AttachmentType;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Descrição do anexo de uma mensagem, gravada dentro do documento da mensagem.
 *
 * <p>O arquivo em si não fica aqui: o servidor só o guarda no armazenamento temporário, por {@code mediaId}, e o apaga
 * no fim do prazo. {@code expiresAt} é até quando ele pode ser retirado. {@code clientPreview} é um texto opaco do
 * cliente (por exemplo uma miniatura minúscula) que o servidor guarda e devolve sem interpretar. Em {@code LOCATION}
 * só valem as coordenadas e o rótulo.</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class Attachment {

    private AttachmentType type;

    @Field("media_id")
    private String mediaId;

    @Field("mime_type")
    private String mimeType;

    @Field("size_bytes")
    private Long sizeBytes;

    private String name;

    @Field("duration_ms")
    private Long durationMs;

    private Integer width;

    private Integer height;

    @Field("client_preview")
    private String clientPreview;

    @Field("expires_at")
    private Instant expiresAt;

    private Double latitude;

    private Double longitude;

    private String label;
}
