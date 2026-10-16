package com.solaria.messenger.openapi;

import com.solaria.messenger.dto.request.CommunityConversationRequestDTO;
import com.solaria.messenger.dto.request.CommunityMembersRequestDTO;
import com.solaria.messenger.dto.request.CommunityRequestDTO;
import com.solaria.messenger.dto.request.CommunityUpdateRequestDTO;
import com.solaria.messenger.dto.response.CommunityResponseDTO;
import com.solaria.messenger.dto.response.ConversationResponseDTO;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

import org.springframework.http.ResponseEntity;

import java.util.List;
import java.util.UUID;

@Tag(name = "Comunidades", description = "Comunidade do projeto, espaço tipo slack")
public interface CommunityOpenApi {

    @Operation(
            summary = "Cria a comunidade de um projeto",
            description =
                    "Inicializa ou retorna a comunidade privada do projeto. Participantes e papéis"
                        + " vêm da API Core; o criador não recebe privilégios adicionais. Exige"
                        + " participação atual no projeto.")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Comunidade criada"),
        @ApiResponse(responseCode = "400", description = "Dados de entrada inválidos"),
        @ApiResponse(responseCode = "403", description = "Usuário não participa do projeto"),
        @ApiResponse(responseCode = "503", description = "Autoridade da API Core indisponível")
    })
    ResponseEntity<CommunityResponseDTO> createCommunity(CommunityRequestDTO dto);

    @Operation(summary = "Lista as comunidades das quais o usuário autenticado é membro")
    @ApiResponses(@ApiResponse(responseCode = "200", description = "Lista retornada com sucesso"))
    ResponseEntity<List<CommunityResponseDTO>> findMine();

    @Operation(summary = "Busca uma comunidade pelo identificador (só membros)")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Comunidade encontrada"),
        @ApiResponse(
                responseCode = "403",
                description = "O usuário autenticado não participa desta comunidade"),
        @ApiResponse(responseCode = "404", description = "Comunidade não encontrada")
    })
    ResponseEntity<CommunityResponseDTO> findById(String id);

    @Operation(summary = "Busca a comunidade de um projeto (só membros)")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Comunidade encontrada"),
        @ApiResponse(
                responseCode = "403",
                description = "O usuário autenticado não participa desta comunidade"),
        @ApiResponse(responseCode = "404", description = "Comunidade não encontrada para o projeto")
    })
    ResponseEntity<CommunityResponseDTO> findByProjectId(UUID projectId);

    @Operation(
            summary = "Atualiza a comunidade",
            description =
                    "Contrato legado: gerencie a situação pela API Core. Alterações diretas"
                        + " retornam 422.")
    @ApiResponses(
            @ApiResponse(
                    responseCode = "422",
                    description = "Use a API Core para gerenciar a situação"))
    ResponseEntity<CommunityResponseDTO> updateCommunity(String id, CommunityUpdateRequestDTO dto);

    @Operation(
            summary = "Adiciona/altera papel de membros",
            description =
                    "Contrato legado: gerencie participantes e papéis pela API Core. Alterações"
                        + " diretas retornam 422.")
    @ApiResponses(
            @ApiResponse(
                    responseCode = "422",
                    description = "Use a API Core para gerenciar participantes"))
    ResponseEntity<CommunityResponseDTO> addMembers(String id, CommunityMembersRequestDTO dto);

    @Operation(
            summary = "Remove um membro | sair da comunidade",
            description =
                    "Contrato legado: remova a participação pela API Core. Alterações diretas"
                        + " retornam 422. A remoção bloqueia novas leituras, envios e entregas"
                        + " WebSocket; o histórico permanece para os demais participantes.")
    @ApiResponses(
            @ApiResponse(
                    responseCode = "422",
                    description = "Use a API Core para remover participantes"))
    ResponseEntity<CommunityResponseDTO> removeMember(String id, UUID userId);

    @Operation(
            summary = "Inicia uma conversa em grupo entre membros da comunidade",
            description =
                    "participantIds opcional -> sem ele, entram todos os membros | se informado"
                        + " apenas os IDs entram. O criador entra sempre.")
    @ApiResponses({
        @ApiResponse(responseCode = "201", description = "Conversa em grupo criada"),
        @ApiResponse(
                responseCode = "403",
                description = "O usuário autenticado não participa desta comunidade"),
        @ApiResponse(responseCode = "404", description = "Comunidade não encontrada"),
        @ApiResponse(
                responseCode = "422",
                description = "Comunidade arquivada ou participante fora da comunidade")
    })
    ResponseEntity<ConversationResponseDTO> startConversation(
            String id, CommunityConversationRequestDTO dto);

    @Operation(summary = "Lista as conversas em grupo de uma comunidade (só membros)")
    @ApiResponses({
        @ApiResponse(responseCode = "200", description = "Lista retornada com sucesso"),
        @ApiResponse(
                responseCode = "403",
                description = "O usuário autenticado não participa desta comunidade"),
        @ApiResponse(responseCode = "404", description = "Comunidade não encontrada")
    })
    ResponseEntity<List<ConversationResponseDTO>> listConversations(String id);
}
