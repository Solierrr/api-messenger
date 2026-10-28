package com.solaria.messenger.core.contract.response;

/** Estado próprio com marcador local para reconciliar respostas HTTP e projeções recebidas por WebSocket. */
public record ConversationStateResponseDTO(
        String conversationId,
        String syncEpoch,
        long syncVersion,
        int lastReadSequence,
        long unreadCount,
        boolean muted,
        boolean archived,
        boolean effectiveMuted) {
}
