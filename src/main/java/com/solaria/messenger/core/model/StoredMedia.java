package com.solaria.messenger.core.model;

import java.time.Instant;
import java.util.UUID;

/**
 * O que o armazenamento sabe de um arquivo depositado: de quem e de qual conversa é, o tipo e o tamanho reais, e
 * quando foi criado (o prazo conta daqui).
 */
public record StoredMedia(String conversationId, UUID uploader, String mimeType, long sizeBytes, Instant createdAt) {
}
