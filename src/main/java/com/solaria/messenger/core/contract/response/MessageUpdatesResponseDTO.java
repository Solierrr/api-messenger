package com.solaria.messenger.core.contract.response;

import java.util.List;

/**
 * Resposta do catch-up de mensagens de {@code GET /messaging/updates}.
 *
 * <p>
 * Devolve o estado atual das mensagens que mudaram depois da versão pedida, em ordem crescente de
 * {@code changeVersion}. O cliente guarda o {@code cursor} e o devolve como {@code since} na próxima chamada.
 * </p>
 *
 * @param syncEpoch época do relógio de sincronização. Se for diferente da guardada pelo cliente, ele refaz a
 *     sincronização completa.
 * @param cursor maior {@code changeVersion} devolvido. Sem mensagens, é o {@code since} recebido. Sem {@code since}, é
 *     a versão atual do relógio.
 * @param hasMore {@code true} quando ainda há mensagens depois do cursor.
 * @param messages mensagens alteradas depois do {@code since}.
 */
public record MessageUpdatesResponseDTO(String syncEpoch, long cursor, boolean hasMore, List<MessageResponseDTO> messages) {
    public MessageUpdatesResponseDTO { messages = List.copyOf(messages); }
}
