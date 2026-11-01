package com.solaria.messenger.core.contract.response;

import java.util.UUID;

/** Convidado que ficou de fora: {@code USER_NOT_FOUND}, {@code USER_INACTIVE} ou {@code NOT_COMMUNITY_MEMBER}. */
public record RejectedParticipantDTO(UUID userId, String reasonCode) {
}
