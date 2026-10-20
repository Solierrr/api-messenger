package com.solaria.messenger.rest.openapi;

import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;

import com.solaria.messenger.core.contract.request.RatingRequestDTO;
import com.solaria.messenger.core.contract.request.RatingStatusUpdateRequestDTO;
import com.solaria.messenger.core.contract.request.RatingUpdateRequestDTO;
import com.solaria.messenger.core.contract.response.RatingResponseDTO;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

/**
 * Contrato Swagger/OpenAPI das avaliações, em {@code /messaging/ratings}.
 *
 * <p>Declara as operações e os códigos de resposta. A implementação fica em
 * {@link com.solaria.messenger.rest.controller.RatingController}.</p>
 *
 * <p>Principais operações disponibilizadas:</p>
 * <ul>
 *     <li>{@link #create(RatingRequestDTO)} - cria uma avaliação.</li>
 *     <li>{@link #findById(String)} - busca uma avaliação pelo identificador.</li>
 *     <li>{@link #findByEvaluated(UUID)} - lista as avaliações recebidas por um avaliado.</li>
 *     <li>{@link #findByEvaluator(UUID)} - lista as avaliações feitas por um avaliador.</li>
 *     <li>{@link #update(String, RatingUpdateRequestDTO)} - edita comentário e/ou nota.</li>
 *     <li>{@link #updateStatus(String, RatingStatusUpdateRequestDTO)} - altera o status.</li>
 * </ul>
 */
@Tag(name = "Avaliações", description = "Avaliações entre usuários, sobre profissional ou produto")
public interface RatingOpenApi {

    /** Contrato OpenAPI: cria uma nova avaliação. */
    @Operation(
        summary = "Cria uma nova avaliação",
        description = "O avaliador (evaluatorId) é sempre o usuário autenticado da requisição. Status inicial é sempre ACTIVE. Não é permitido avaliar a si mesmo."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Avaliação criada com sucesso"),
            @ApiResponse(responseCode = "400", description = "Dados de entrada inválidos"),
            @ApiResponse(responseCode = "422", description = "Autoavaliação não permitida")
    })
    ResponseEntity<RatingResponseDTO> create(RatingRequestDTO dto);

    /** Contrato OpenAPI: busca uma avaliação pelo identificador. */
    @Operation(
        summary = "Busca uma avaliação pelo identificador",
        description = "qualquer usuário pode ler uma avaliação."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Avaliação encontrada"),
            @ApiResponse(responseCode = "404", description = "Avaliação não encontrada")
    })
    ResponseEntity<RatingResponseDTO> findById(String id);

    /** Contrato OpenAPI: lista as avaliações recebidas por um avaliado. */
    @Operation(
        summary = "Lista as avaliações de um usuário/produto",
        description = "Reputação pública do avaliado(produto/profissional)."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Lista de avaliações retornada com sucesso")
    })
    ResponseEntity<List<RatingResponseDTO>> findByEvaluated(UUID evaluatedId);

    /** Contrato OpenAPI: lista as avaliações dadas por um usuário. */
    @Operation(
        summary = "Lista as avaliações dadas por um usuário",
        description = "Listagem pública (F-17): qualquer usuário pode listar as avaliações dadas por outro."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Lista de avaliações retornada com sucesso")
    })
    ResponseEntity<List<RatingResponseDTO>> findByEvaluator(UUID evaluatorId);

    /** Contrato OpenAPI: edita o comentário e/ou a nota de uma avaliação. */
    @Operation(
        summary = "Edita o comentário e/ou a nota de uma avaliação",
        description = "Somente o autor original pode editá-la."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Avaliação atualizada com sucesso"),
            @ApiResponse(responseCode = "400", description = "Dados de entrada inválidos"),
            @ApiResponse(responseCode = "403", description = "Usuário autenticado não é o autor da avaliação"),
            @ApiResponse(responseCode = "404", description = "Avaliação não encontrada")
    })
    ResponseEntity<RatingResponseDTO> update(String id, RatingUpdateRequestDTO dto);

    /** Contrato OpenAPI: altera o status de uma avaliação. */
    @Operation(
        summary = "Altera o status de uma avaliação",
        description = "Somente o dono da avalição pode alterar o status da avaliação"
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Status atualizado com sucesso"),
            @ApiResponse(responseCode = "400", description = "Dados de entrada inválidos"),
            @ApiResponse(responseCode = "403", description = "Usuário autenticado não é o autor da avaliação"),
            @ApiResponse(responseCode = "404", description = "Avaliação não encontrada")
    })
    ResponseEntity<RatingResponseDTO> updateStatus(String id, RatingStatusUpdateRequestDTO dto);
}
