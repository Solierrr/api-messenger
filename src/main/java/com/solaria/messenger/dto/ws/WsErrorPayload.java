package com.solaria.messenger.dto.ws;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Payload de erro do user atual dentro de um frame STOMP autenticado sem que a conexõ seja cortada
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

    /** Preenchido apenas quando o erro se origina de um envio com clientMessageId */
    private String clientMessageId;

    /** nulo por enquanto*/
    private String traceId;
}
