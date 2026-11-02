package com.solaria.messenger.core.contract.response;

import java.time.Instant;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.solaria.messenger.core.model.enums.AttachmentType;

/**
 * Descrição do anexo devolvida na mensagem, no histórico, no catch-up e nos eventos.
 *
 * <p>Não leva endereço do arquivo: {@code downloadPath} é o caminho da retirada, que confere a participação e
 * redireciona para um link temporário. {@code expiresAt} é até quando o arquivo pode ser retirado. Campos que não valem
 * para o tipo ficam fora do JSON.</p>
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record AttachmentResponseDTO(
        AttachmentType type,
        String mediaId,
        String mimeType,
        Long sizeBytes,
        String name,
        Long durationMs,
        Integer width,
        Integer height,
        String clientPreview,
        Instant expiresAt,
        String downloadPath,
        Double latitude,
        Double longitude,
        String label) {
}
