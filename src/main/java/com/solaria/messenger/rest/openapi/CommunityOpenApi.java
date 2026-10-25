package com.solaria.messenger.rest.openapi;

import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;

import com.solaria.messenger.rest.dto.request.CommunityConversationRequestDTO;
import com.solaria.messenger.core.contract.request.CommunityRequestDTO;
import com.solaria.messenger.core.contract.request.CommunityUpdateRequestDTO;
import com.solaria.messenger.core.contract.response.CommunityResponseDTO;
import com.solaria.messenger.core.contract.response.GroupCreationResponseDTO;
import com.solaria.messenger.core.contract.response.SubgroupMetadataResponseDTO;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Contrato Swagger/OpenAPI dos recursos de comunidade, em {@code /messaging/communities}.
 *
 * <p>Declara as operações e os códigos de resposta. A implementação fica em
 * {@link com.solaria.messenger.rest.controller.CommunityController}.</p>
 *
 * <p>Principais operações disponibilizadas:</p>
 * <ul>
 *     <li>{@link #createCommunity(CommunityRequestDTO)} - cria a comunidade de um projeto.</li>
 *     <li>{@link #findMine()} - lista as comunidades do usuário autenticado.</li>
 *     <li>{@link #updateCommunity(String, CommunityUpdateRequestDTO)} - altera o nome e a descrição da comunidade.</li>
 *     <li>{@link #startConversation(String, CommunityConversationRequestDTO)} - inicia um subgrupo.</li>
 *     <li>{@link #listConversations(String)} - lista os subgrupos da comunidade.</li>
 * </ul>
 */
@Tag(name = "Comunidades", description = "Comunidade do projeto, espaço tipo slack")
public interface CommunityOpenApi {

    /** Contrato OpenAPI: cria a comunidade de um projeto. */
    @Operation(
        summary = "Cria a comunidade de um projeto",
        description = "Membros e papéis vêm do projeto na api-core (OWNER -> OWNER, MANAGER -> ADMIN, MEMBER -> MEMBER, "
        + "VIEWER -> VIEWER) | o nome começa como o do projeto | memberIds não concede participação | "
        + "Uma comunidade por projeto: se já existir, "
        + "é sincronizada e devolvida. Projeto só para consulta deixa a comunidade ARCHIVED."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Comunidade criada ou existente sincronizada"),
            @ApiResponse(responseCode = "400", description = "Dados de entrada inválidos"),
            @ApiResponse(responseCode = "403", description = "Usuário autenticado não participa do projeto"),
            @ApiResponse(responseCode = "503", description = "api-core indisponível ou chave de serviço não configurada")
    })
    ResponseEntity<CommunityResponseDTO> createCommunity(CommunityRequestDTO dto);

    /** Contrato OpenAPI: lista as comunidades das quais o usuário é membro. */
    @Operation(summary = "Lista as comunidades das quais o usuário autenticado é membro")
    @ApiResponses(@ApiResponse(responseCode = "200", description = "Lista retornada com sucesso"))
    ResponseEntity<List<CommunityResponseDTO>> findMine();

    /** Contrato OpenAPI: busca uma comunidade pelo identificador. */
    @Operation(summary = "Busca uma comunidade pelo identificador (só membros)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Comunidade encontrada"),
            @ApiResponse(responseCode = "403", description = "O usuário autenticado não participa do projeto da comunidade"),
            @ApiResponse(responseCode = "404", description = "Comunidade não encontrada")
    })
    ResponseEntity<CommunityResponseDTO> findById(String id);

    /** Contrato OpenAPI: busca a comunidade de um projeto. */
    @Operation(summary = "Busca a comunidade de um projeto (só membros)")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Comunidade encontrada"),
            @ApiResponse(responseCode = "403", description = "O usuário autenticado não participa do projeto da comunidade"),
            @ApiResponse(responseCode = "404", description = "Comunidade não encontrada para o projeto")
    })
    ResponseEntity<CommunityResponseDTO> findByProjectId(UUID projectId);

    /** Contrato OpenAPI: altera o nome e a descrição da comunidade. */
    @Operation(
        summary = "Altera o nome e a descrição da comunidade",
        description = "OWNER e ADMIN editam nome e descrição; campo ausente fica como está, descrição vazia apaga. "
                + "Membros e status seguem o projeto na api-core e não mudam por aqui. Comunidade ARCHIVED não é editada."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Comunidade atualizada ou sem mudança"),
            @ApiResponse(responseCode = "400", description = "INVALID_FIELD; nenhum campo informado, nome vazio ou longo demais"),
            @ApiResponse(responseCode = "403", description = "Usuário fora do projeto ou sem papel OWNER/ADMIN"),
            @ApiResponse(responseCode = "404", description = "Comunidade não encontrada"),
            @ApiResponse(responseCode = "409", description = "CONCURRENT_MODIFICATION"),
            @ApiResponse(responseCode = "422", description = "Comunidade arquivada"),
            @ApiResponse(responseCode = "503", description = "api-core indisponível")
    })
    ResponseEntity<CommunityResponseDTO> updateCommunity(String id, CommunityUpdateRequestDTO dto);

    /** Contrato OpenAPI: inicia um subgrupo entre membros da comunidade. */
    @Operation(
        summary = "Inicia uma conversa em grupo entre membros da comunidade",
        description = "participantIds omitido ou vazio seleciona os membros atuais; se informado, seleciona os IDs pedidos. "
                + "O criador autorizado entra sempre, com ao menos outro participante elegível. "
                + "Convidados individualmente inelegíveis são excluídos e retornados com motivo genérico. "
                + "Falhas técnicas abortam o comando; conversa e estados pessoais são atômicos, sem retry de criação."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Subgrupo criado, participantes admitidos e rejeições genéricas",
                    content = @Content(schema = @Schema(implementation = GroupCreationResponseDTO.class))),
            @ApiResponse(responseCode = "400", description = "Dados de entrada inválidos"),
            @ApiResponse(responseCode = "403", description = "Criador não participa da comunidade, é VIEWER ou está inativo"),
            @ApiResponse(responseCode = "404", description = "Comunidade não encontrada"),
            @ApiResponse(responseCode = "422", description = "Comunidade arquivada ou menos de dois participantes distintos elegíveis"),
            @ApiResponse(responseCode = "503", description = "Auth/banco indisponível ou resultado da transação não confirmado")
    })
    ResponseEntity<GroupCreationResponseDTO> startConversation(String id, CommunityConversationRequestDTO dto);

    /** Contrato OpenAPI: lista os metadados dos subgrupos da comunidade. */
    @Operation(summary = "Lista metadados dos subgrupos da comunidade",
            description = "OWNER/ADMIN atuais veem todos os GROUPs; MEMBER e VIEWER veem apenas os GROUPs de que participa. "
                    + "Disponível em ARCHIVED, com sete campos e nulos explícitos; não concede acesso ao conteúdo.")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Lista retornada com sucesso"),
            @ApiResponse(responseCode = "403", description = "O usuário autenticado não participa do projeto da comunidade"),
            @ApiResponse(responseCode = "404", description = "Comunidade não encontrada")
    })
    ResponseEntity<List<SubgroupMetadataResponseDTO>> listConversations(String id);
}
