package com.solaria.messenger.openapi;

import org.springframework.http.ResponseEntity;

import com.solaria.messenger.dto.request.ChatbotMessageRequestDTO;
import com.solaria.messenger.dto.response.MessageResponseDTO;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(
    name = "Mensagens do chatbot (interno)",
    description = "Publicação da resposta do bot numa conversa. Endpoint interno, em /internal/messages, alcançável apenas pela rede interna (nunca via Kong)."
)
public interface ChatbotMessageOpenApi {

    @Operation(
        summary = "Publica a resposta do bot numa conversa existente",
        description = "messageType é sempre CHATBOT_TO_USER e role sempre \"assistant\""
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Mensagem publicada com sucesso"),
            @ApiResponse(responseCode = "400", description = "Dados de entrada inválidos"),
            @ApiResponse(responseCode = "404", description = "Conversa não encontrada")
    })
    ResponseEntity<MessageResponseDTO> ingestChatbotMessage(ChatbotMessageRequestDTO dto);
}
