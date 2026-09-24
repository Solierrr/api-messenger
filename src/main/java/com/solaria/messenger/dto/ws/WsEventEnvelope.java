package com.solaria.messenger.dto.ws;

import java.time.Instant;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * payload padrão de qualquer frame STOMP("request http" do protocolo websocket ) 
 * Todo evento é passado por esse DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class WsEventEnvelope {

    private String type;

    private String conversationId;

    private String eventId;

    private Instant serverTimestamp;

    private Object payload;
}
