package com.solaria.messenger.core.contract.response;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;


/**
 * DTO de resposta com os metadados de um subgrupo de comunidade.
 *
 * <p>Retornado por {@code GET /messaging/communities/{id}/conversations}. O construtor compacto
 * copia os participantes em um conjunto imutável, e {@code null} vira conjunto vazio.</p>
 */
public record SubgroupMetadataResponseDTO(
        String id,
        String title,
        Set<UUID> participantIds,
        UUID managerUserId,
        Instant startedAt,
        Instant lastInteractionAt) {

    public SubgroupMetadataResponseDTO {
        participantIds = participantIds == null ? Set.of() : Set.copyOf(participantIds);
    }
}
