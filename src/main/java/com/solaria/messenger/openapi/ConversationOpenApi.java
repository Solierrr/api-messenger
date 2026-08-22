package com.solaria.messenger.openapi;

import java.util.List;

import org.springframework.http.ResponseEntity;

import com.solaria.messenger.dto.request.ChatbotConversationRequestDTO;
import com.solaria.messenger.dto.request.UserConversationRequestDTO;
import com.solaria.messenger.dto.response.ConversationResponseDTO;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Conversas", description = "Gerenciamento de conversas pessoa-a-pessoa e com o chatbot")
public interface ConversationOpenApi {

    @Operation(
        summary = "Inicia uma conversa pessoa-a-pessoa",
        description = "senderId nunca é aceito do cliente -> é sempre o usuário autenticado (claim \"sub\" do JWT)."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Conversa criada com sucesso"),
            @ApiResponse(responseCode = "400", description = "Dados de entrada inválidos")
    })
    ResponseEntity<ConversationResponseDTO> createUserConversation(UserConversationRequestDTO dto);

    @Operation(
        summary = "Inicia uma conversa com o chatbot",
        description = "receiverId nunca é aceito do cliente -> é sempre o usuário autenticado (claim \"sub\" do JWT)."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Conversa criada com sucesso"),
            @ApiResponse(responseCode = "400", description = "Dados de entrada inválidos")
    })
    ResponseEntity<ConversationResponseDTO> createChatbotConversation(ChatbotConversationRequestDTO dto);

    @Operation(
        summary = "Busca uma conversa pelo identificador",
        description = "Numa conversa pessoa-a-pessoa, tanto o remetente quanto o destinatário podem acessá-la, numa conversa com o bot, só o próprio usuário pode."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Conversa encontrada"),
            @ApiResponse(responseCode = "403", description = "O usuário autenticado não participa desta conversa"),
            @ApiResponse(responseCode = "404", description = "Conversa não encontrada")
    })
    ResponseEntity<ConversationResponseDTO> findById(String id);

    @Operation(summary = "Lista as conversas (pessoa-a-pessoa e com o chatbot) do usuário autenticado")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Lista de conversas retornada com sucesso")
    })
    ResponseEntity<List<ConversationResponseDTO>> findMine();
}
