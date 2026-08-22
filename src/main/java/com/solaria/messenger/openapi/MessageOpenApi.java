package com.solaria.messenger.openapi;

import java.util.List;

import org.springframework.http.ResponseEntity;

import com.solaria.messenger.dto.request.MessageRequestDTO;
import com.solaria.messenger.dto.response.MessageResponseDTO;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Mensagens", description = "Mensagens trocadas por pessoas, entre si ou com o chatbot")
public interface MessageOpenApi {

    @Operation(
        summary = "Envia uma mensagem de uma pessoa numa conversa existente",
        description = "Aceita messageType USER_TO_USER ou USER_TO_CHATBOT."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Mensagem enviada com sucesso"),
            @ApiResponse(responseCode = "400", description = "Dados de entrada inválidos, incluindo messageType CHATBOT_TO_USER"),
            @ApiResponse(responseCode = "403", description = "O usuário autenticado não participa desta conversa"),
            @ApiResponse(responseCode = "404", description = "Conversa não encontrada")
    })
    ResponseEntity<MessageResponseDTO> sendMessage(MessageRequestDTO dto);

    @Operation(
        summary = "Lista as mensagens de uma conversa",
        description = "Lista todas as mensagens de uma conversa do usuario"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Lista de mensagens retornada com sucesso"),
            @ApiResponse(responseCode = "403", description = "O usuário autenticado não participa desta conversa"),
            @ApiResponse(responseCode = "404", description = "Conversa não encontrada")
    })
    ResponseEntity<List<MessageResponseDTO>> getMessagesByConversationId(String conversationId);
}
