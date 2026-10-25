package com.solaria.messenger.core.contract.response;

import java.util.UUID;

/** Informa somente o ID solicitado e um motivo genérico, sem revelar dados da conta. */
public record RejectedParticipantDTO(UUID userId, String reasonCode) {
}
