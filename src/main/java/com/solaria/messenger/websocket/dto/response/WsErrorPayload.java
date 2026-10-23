package com.solaria.messenger.websocket.dto.response;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Payload de erro do user atual dentro de um frame STOMP autenticado sem que a conexão seja cortada
 * possíveis erros:
 * erro de negocio em {@code /app/messages.send}
 * autorizacao negada em um {@code SUBSCRIBE}
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WsErrorPayload {

    private String code;

    private String message;

    private String clientMessageId;

    /** nulo por enquanto*/
    private String traceId;
}
