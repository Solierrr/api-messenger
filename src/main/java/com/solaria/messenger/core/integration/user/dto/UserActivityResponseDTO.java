package com.solaria.messenger.core.integration.user.dto;

import java.util.UUID;

/**
 * Atividade de um usuário na api-auth
 */
public record UserActivityResponseDTO(UUID authId, Boolean active) {
}
