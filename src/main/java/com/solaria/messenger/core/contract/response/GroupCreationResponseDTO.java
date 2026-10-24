package com.solaria.messenger.core.contract.response;

import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Resultado da seleção de convidados e da criação confirmada da conversa. */
public record GroupCreationResponseDTO(ConversationResponseDTO conversation, Set<UUID> admittedParticipantIds,
        List<RejectedParticipantDTO> rejectedParticipants) {
    public GroupCreationResponseDTO {
        admittedParticipantIds = Set.copyOf(admittedParticipantIds);
        rejectedParticipants = List.copyOf(rejectedParticipants);
    }
}
