package com.solaria.messenger.rest.dto.response;

import java.util.UUID;

/** Resposta da transferência de gestão, preservando o identificador e o gestor vigente. */
public record ManagerTransferResponse(String conversationId, UUID managerUserId) {
}
