package com.solaria.messenger.core.integration.core.dto;

import java.util.Map;
import java.util.UUID;

/**
 * Acesso de um usuário a um projeto na api-core: o papel dele, se o projeto está só para consulta,
 * os envolvidos (authId -> papel) e o nome do projeto
 */
public record ProjectAccessResponseDTO(String role, boolean readOnly, Map<UUID, String> members, String name) {
}
