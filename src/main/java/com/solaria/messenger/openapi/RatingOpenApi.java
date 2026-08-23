package com.solaria.messenger.openapi;

import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;

import com.solaria.messenger.dto.request.RatingRequestDTO;
import com.solaria.messenger.dto.request.RatingStatusUpdateRequestDTO;
import com.solaria.messenger.dto.request.RatingUpdateRequestDTO;
import com.solaria.messenger.dto.response.RatingResponseDTO;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(name = "Avaliações", description = "Avaliações entre usuários, sobre profissional ou produto")
public interface RatingOpenApi {

    @Operation(
        summary = "Cria uma nova avaliação",
        description = "O avaliador (evaluatorId) é sempre o usuário autenticado da requisição. Status inicial é sempre ACTIVE."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Avaliação criada com sucesso"),
            @ApiResponse(responseCode = "400", description = "Dados de entrada inválidos")
    })
    ResponseEntity<RatingResponseDTO> create(RatingRequestDTO dto);

    @Operation(
        summary = "Busca uma avaliação pelo identificador",
        description = "qualquer usuário pode ler uma avaliação."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Avaliação encontrada"),
            @ApiResponse(responseCode = "404", description = "Avaliação não encontrada")
    })
    ResponseEntity<RatingResponseDTO> findById(String id);

    @Operation(
        summary = "Lista as avaliações de um usuário/produto",
        description = "Reputação pública do avaliado(produto/profissional)."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Lista de avaliações retornada com sucesso")
    })
    ResponseEntity<List<RatingResponseDTO>> findByEvaluated(UUID evaluatedId);

    @Operation(
        summary = "Lista as avaliações dadas por um usuário",
        description = "só o próprio avaliador pode listar as avaliações que ele deu."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Lista de avaliações retornada com sucesso"),
            @ApiResponse(responseCode = "403", description = "Usuário autenticado não é o avaliador informado")
    })
    ResponseEntity<List<RatingResponseDTO>> findByEvaluator(UUID evaluatorId);

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
