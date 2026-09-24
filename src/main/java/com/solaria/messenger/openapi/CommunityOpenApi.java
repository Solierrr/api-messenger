package com.solaria.messenger.openapi;

import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;

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

@Tag(name = "Comunidades", description = "Comunidade do projeto, espaço tipo slack")
public interface CommunityOpenApi {

    @Operation(
        summary = "Cria a comunidade de um projeto",
        description = "Quem cria vira OWNER | memberIds opcionais entram como MEMBER | Uma comunidade por projeto"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Comunidade criada"),
            @ApiResponse(responseCode = "400", description = "Dados de entrada inválidos"),
            @ApiResponse(responseCode = "409", description = "Já existe uma comunidade para o projeto")
    })
    ResponseEntity<CommunityResponseDTO> createCommunity(CommunityRequestDTO dto);

    @Operation(summary = "Lista as comunidades das quais o usuário autenticado é membro")
    @ApiResponses(@ApiResponse(responseCode = "200", description = "Lista retornada com sucesso"))
    ResponseEntity<List<CommunityResponseDTO>> findMine();

    @Operation(summary = "Busca uma comunidade pelo identificador (só membros)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Comunidade encontrada"),
            @ApiResponse(responseCode = "403", description = "O usuário autenticado não participa desta comunidade"),
            @ApiResponse(responseCode = "404", description = "Comunidade não encontrada")
    })
    ResponseEntity<CommunityResponseDTO> findById(String id);

    @Operation(summary = "Busca a comunidade de um projeto (só membros)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Comunidade encontrada"),
            @ApiResponse(responseCode = "403", description = "O usuário autenticado não participa desta comunidade"),
            @ApiResponse(responseCode = "404", description = "Comunidade não encontrada para o projeto")
    })
    ResponseEntity<CommunityResponseDTO> findByProjectId(UUID projectId);

    @Operation(
        summary = "Atualiza a comunidade",
        description = " unico campo editável é o status | alterar para ARCHIVED exige OWNER"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Comunidade atualizada"),
            @ApiResponse(responseCode = "403", description = "Papel insuficiente na comunidade"),
            @ApiResponse(responseCode = "404", description = "Comunidade não encontrada")
    })
    ResponseEntity<CommunityResponseDTO> updateCommunity(String id, CommunityUpdateRequestDTO dto);

    @Operation(
        summary = "Adiciona/altera papel de membros",
        description = "Adicionar MEMBER exige ADMIN | conceder ADMIN exige OWNER"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Membros adicionados"),
            @ApiResponse(responseCode = "403", description = "Papel insuficiente na comunidade"),
            @ApiResponse(responseCode = "404", description = "Comunidade não encontrada"),
            @ApiResponse(responseCode = "422", description = "Comunidade arquivada ou papel OWNER solicitado")
    })
    ResponseEntity<CommunityResponseDTO> addMembers(String id, CommunityMembersRequestDTO dto);

    @Operation(
        summary = "Remove um membro | sair da comunidade",
        description = "Cada um pode sair | remover outros exige OWNER/ADMIN | OWNER não pode ser removido nem sair"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Membro removido"),
            @ApiResponse(responseCode = "403", description = "Sem permissão para remover esse membro"),
            @ApiResponse(responseCode = "404", description = "Comunidade ou membro não encontrado"),
            @ApiResponse(responseCode = "422", description = "Tentativa de remover o OWNER")
    })
    ResponseEntity<CommunityResponseDTO> removeMember(String id, UUID userId);

    @Operation(
        summary = "Inicia uma conversa em grupo entre membros da comunidade",
        description = "participantIds opcional -> sem ele, entram todos os membros | se informado apenas os IDs entram. "
                + "O criador entra sempre."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Conversa em grupo criada"),
            @ApiResponse(responseCode = "403", description = "O usuário autenticado não participa desta comunidade"),
            @ApiResponse(responseCode = "404", description = "Comunidade não encontrada"),
            @ApiResponse(responseCode = "422", description = "Comunidade arquivada ou participante fora da comunidade")
    })
    ResponseEntity<ConversationResponseDTO> startConversation(String id, CommunityConversationRequestDTO dto);

    @Operation(summary = "Lista as conversas em grupo de uma comunidade (só membros)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Lista retornada com sucesso"),
            @ApiResponse(responseCode = "403", description = "O usuário autenticado não participa desta comunidade"),
            @ApiResponse(responseCode = "404", description = "Comunidade não encontrada")
    })
    ResponseEntity<List<ConversationResponseDTO>> listConversations(String id);
}
