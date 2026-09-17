package com.solaria.messenger.openapi;

import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;

import com.solaria.messenger.dto.request.ChatbotConversationRequestDTO;
import com.solaria.messenger.dto.request.DirectConversationRequestDTO;
import com.solaria.messenger.dto.request.GroupConversationRequestDTO;
import com.solaria.messenger.dto.request.ParticipantsRequestDTO;
import com.solaria.messenger.dto.response.ConversationResponseDTO;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Conversas", description = "Conversas diretas, em grupo e com o chatbot")
public interface ConversationOpenApi {

    @Operation(
        summary = "Inicia  uma conversa entre duas pessoas/direta",
        description = "O inciador da conversa é o id do JWT do usuario autenticado |"
                + " se já existir uma conversa DIRECT ativa entre os dois, ela é retornada."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Conversa criada ou reaproveitada"),
            @ApiResponse(responseCode = "400", description = "Dados inválidos, incluindo conversa consigo mesmo")
    })
    ResponseEntity<ConversationResponseDTO> createDirectConversation(DirectConversationRequestDTO dto);

    @Operation(
        summary = "Inicia uma conversa em grupo ",
        description = "O criador entra automaticamente nos participantes | é preciso ter pelo menos 2 participantes"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Grupo criado com sucesso"),
            @ApiResponse(responseCode = "400", description = "Dados de entrada inválidos")
    })
    ResponseEntity<ConversationResponseDTO> createGroupConversation(GroupConversationRequestDTO dto);

    @Operation(
        summary = "Inicia uma conversa com o chatbot",
        description = " o inciador da conversa é o id do JWT do usuario autenticado"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Conversa criada com sucesso"),
            @ApiResponse(responseCode = "400", description = "Dados de entrada inválidos")
    })
    ResponseEntity<ConversationResponseDTO> createChatbotConversation(ChatbotConversationRequestDTO dto);

    @Operation(
        summary = "Busca uma conversa pelo identificador",
        description = "Só participantes da conversa podem acessá-la"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Conversa encontrada"),
            @ApiResponse(responseCode = "403", description = "O usuário autenticado não participa desta conversa"),
            @ApiResponse(responseCode = "404", description = "Conversa não encontrada")
    })
    ResponseEntity<ConversationResponseDTO> findById(String id);

    @Operation(summary = "Lista as conversas (DIRECT, GROUP e CHAT_BOT) do usuário autenticado")
    @ApiResponses(@ApiResponse(responseCode = "200", description = "Lista retornada com sucesso"))
    ResponseEntity<List<ConversationResponseDTO>> findMine();

    @Operation(
        summary = "Adiciona participantes a uma conversa em grupo",
        description = "Só participantes do grupo podem adicionar | Se o grupo pertence a uma comunidade, "
                + "os novos participantes precisam ser membros dela"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Participantes adicionados"),
            @ApiResponse(responseCode = "403", description = "O usuário autenticado não participa deste grupo"),
            @ApiResponse(responseCode = "404", description = "Conversa não encontrada"),
            @ApiResponse(responseCode = "422", description = "Conversa não é um grupo, está desativada, ou participante fora da comunidade")
    })
    ResponseEntity<ConversationResponseDTO> addParticipants(String id, ParticipantsRequestDTO dto);

    @Operation(
        summary = "Remove um participante de um grupo ou sai do grupo",
        description = "Cada um pode sair do próprio grupo | remover outros exige ser quem criou o grupo."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Participante removido"),
            @ApiResponse(responseCode = "403", description = "Sem permissão para remover esse participante"),
            @ApiResponse(responseCode = "404", description = "Conversa não encontrada")
    })
    ResponseEntity<ConversationResponseDTO> removeParticipant(String id, UUID userId);
}
