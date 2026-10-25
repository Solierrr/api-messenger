package com.solaria.messenger.core.contract.response;

import java.util.List;

/** Fronteira da consulta completa, inclusive lista vazia; metadados são enviados como headers HTTP. */
public record ConversationListSnapshotDTO(String syncEpoch, long syncVersion, List<ConversationListItemResponseDTO> items) {
    public ConversationListSnapshotDTO { items = List.copyOf(items); }
}
