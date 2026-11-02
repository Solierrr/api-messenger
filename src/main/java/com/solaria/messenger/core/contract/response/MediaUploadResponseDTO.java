package com.solaria.messenger.core.contract.response;

import java.time.Instant;
import java.util.Map;

/**
 * Permissão para depositar um arquivo.
 *
 * <p>O cliente envia os bytes com {@code upload.method} para {@code upload.url}, mandando exatamente os
 * {@code upload.headers} (eles entram na assinatura), e só então envia a mensagem com o {@code mediaId}. A permissão
 * pode ser repetida até {@code uploadExpiresAt}.</p>
 *
 * @param mediaId identificador do arquivo, a citar no anexo da mensagem.
 * @param upload para onde e como enviar.
 * @param uploadExpiresAt até quando a permissão de envio vale.
 * @param maxBytes tamanho máximo aceito para este tipo.
 */
public record MediaUploadResponseDTO(String mediaId, Target upload, Instant uploadExpiresAt, long maxBytes) {

    /** Destino do envio dos bytes. */
    public record Target(String url, String method, Map<String, String> headers) {
    }
}
