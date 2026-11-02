package com.solaria.messenger.core.model.enums;

/**
 * Tipos de anexo de uma mensagem.
 *
 * <ul>
 *   <li>{@code IMAGE}, {@code VIDEO}, {@code AUDIO}, {@code FILE} -> têm arquivo, que o servidor guarda por um prazo
 *       curto só para o transferir aos aparelhos</li>
 *   <li>{@code LOCATION} -> só coordenadas e rótulo, sem arquivo</li>
 * </ul>
 */
public enum AttachmentType {
    IMAGE,
    VIDEO,
    AUDIO,
    FILE,
    LOCATION;

    /** Verdadeiro quando o anexo tem um arquivo no armazenamento temporário. */
    public boolean hasFile() {
        return this != LOCATION;
    }
}
