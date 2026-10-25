package com.solaria.messenger.core.integration.user.dto;

import java.util.List;
import java.util.UUID;
import com.solaria.messenger.core.integration.user.UserAccessClient;

/**
 * Resposta da consulta em lote de atividade de usuários na api-auth.
 *
 * <p>Separa os usuários encontrados ({@link UserActivityResponseDTO}) dos IDs pedidos que não
 * existem na api-auth ({@code missingAuthIds}). Validada por {@link UserAccessClient}.</p>
 */
public record UserActivityBatchResponseDTO(List<UserActivityResponseDTO> users, List<UUID> missingAuthIds) {
}
