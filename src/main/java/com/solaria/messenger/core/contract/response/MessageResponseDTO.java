package com.solaria.messenger.core.contract.response;

import java.time.Instant;
import java.util.UUID;

import com.solaria.messenger.core.model.MessageMetadata;
import com.solaria.messenger.core.model.enums.MessageType;
import io.swagger.v3.oas.annotations.media.Schema;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * DTO de resposta com os dados de uma mensagem.
 *
 * <p>Retornado pelos endpoints de envio, edição, remoção e listagem de {@code /messaging/messages},
 * pelo catch-up de {@code /messaging/updates}, pelo endpoint interno de ingestão do chatbot e como payload dos eventos
 * {@code MESSAGE_*} em {@code /topic/users.{userId}.events}.</p>
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MessageResponseDTO {

    private String id;
    private String conversationId;
    private UUID senderId;
    /** Correlação opcional, sem unicidade; a identidade persistida é id. */
    @Schema(nullable = true, description = "Correlação informada pelo cliente ou null; identidade persistida em id")
    private UUID clientMessageId;
    private String role;
    private MessageType messageType;
    private String content;
    private boolean edited;
    private boolean deleted;
    private MessageMetadata metadata;
    private Instant timestamp;
    private int sequence;
    /** Versão do relógio de sincronização da última mudança (envio, edição ou exclusão). A maior vence. */
    @Schema(description = "Versão da última mudança da mensagem. O cliente mantém a de maior valor. Vale 0 em mensagem sem carimbo")
    private long changeVersion;
}
