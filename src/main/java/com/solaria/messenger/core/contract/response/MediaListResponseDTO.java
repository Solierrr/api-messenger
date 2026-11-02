package com.solaria.messenger.core.contract.response;

import java.util.List;

/**
 * Página de mídias ainda disponíveis, da mais recente para a mais antiga.
 *
 * @param items mídias da página.
 * @param nextCursor valor opaco para pedir a página seguinte, ou nulo quando não há mais.
 */
public record MediaListResponseDTO(List<MediaListItemResponseDTO> items, String nextCursor) {
    public MediaListResponseDTO {
        items = List.copyOf(items);
    }
}
