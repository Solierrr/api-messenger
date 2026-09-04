package com.solaria.messenger.openapi;

import java.util.List;

import org.springframework.http.ResponseEntity;

import com.solaria.messenger.dto.request.LlmObservabilityRequestDTO;
import com.solaria.messenger.dto.response.LlmObservabilityResponseDTO;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;

@Tag(
    name = "Observabilidade de LLM",
description = "Telemetria interna do pipeline de LLM -> roteador/nós do grafo reportam métricas de cada passo (chamada a modelo ou a ferramenta). "
    + "Endpoints internos, em /internal/observability, alcançáveis apenas pela rede interna (nunca via Kong)."
)
public interface LlmObservabilityOpenApi {

    @Operation(
        summary = "Registra um evento de telemetria de um passo do pipeline de LLM",
        description = "Log de auditoria imutável -> não há update/delete. Se timestamp não for informado o horário de ingestão (Instant.now()) é usado."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "201", description = "Evento registrado com sucesso"),
            @ApiResponse(responseCode = "400", description = "Dados de entrada inválidos")
    })
    ResponseEntity<LlmObservabilityResponseDTO> ingest(LlmObservabilityRequestDTO dto);

    @Operation(summary = "Busca um evento de telemetria pelo identificador")
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Evento encontrado"),
            @ApiResponse(responseCode = "404", description = "Evento não encontrado")
    })
    ResponseEntity<LlmObservabilityResponseDTO> findById(String id);

    @Operation(
        summary = "Lista/filtra eventos de telemetria",
        description = "No máximo um filtro (conversationId, node ou status) pode ser informado por chamada "
                + "nesta versão; nenhum filtro informado devolve todos os eventos."
    )
    @ApiResponses({
            @ApiResponse(responseCode = "200", description = "Lista de eventos retornada com sucesso"),
            @ApiResponse(responseCode = "400", description = "Mais de um filtro informado simultaneamente")
    })
    ResponseEntity<List<LlmObservabilityResponseDTO>> search(
            @Parameter(description = "Filtra pelo identificador da conversa", required = false) String conversationId,
            @Parameter(description = "Filtra pelo nó do grafo que originou a chamada", required = false) String node,
            @Parameter(description = "Filtra pelo status da chamada ( erro -> false / ok-> true)", required = false) String status);
}
