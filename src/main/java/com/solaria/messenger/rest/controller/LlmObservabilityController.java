package com.solaria.messenger.rest.controller;

import java.net.URI;
import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.solaria.messenger.core.contract.request.LlmObservabilityRequestDTO;
import com.solaria.messenger.core.contract.response.LlmObservabilityResponseDTO;
import com.solaria.messenger.rest.openapi.LlmObservabilityOpenApi;
import com.solaria.messenger.core.service.LlmObservabilityService;

import jakarta.validation.Valid;

/** Expõe o contrato interno de telemetria, sem acoplar ingestão aos comandos de mensagem */
@RestController
@RequestMapping("/internal/observability")
public class LlmObservabilityController implements LlmObservabilityOpenApi {

    private final LlmObservabilityService llmObservabilityService;

    public LlmObservabilityController(LlmObservabilityService llmObservabilityService) {
        this.llmObservabilityService = llmObservabilityService;
    }

    /** Registra um evento de telemetria do pipeline LLM e devolve 201 */
    @Override
    @PostMapping
    public ResponseEntity<LlmObservabilityResponseDTO> ingest(@Valid @RequestBody LlmObservabilityRequestDTO dto) {
        LlmObservabilityResponseDTO response = llmObservabilityService.ingest(dto);
        return ResponseEntity.created(URI.create("/internal/observability/" + response.getId())).body(response);
    }

    /** Busca um evento de telemetria pelo ID */
    @Override
    @GetMapping("/{id}")
    public ResponseEntity<LlmObservabilityResponseDTO> findById(@PathVariable String id) {
        return ResponseEntity.ok(llmObservabilityService.findById(id));
    }

    /** Lista eventos de telemetria filtrando por no máximo um critério */
    @Override
    @GetMapping
    public ResponseEntity<List<LlmObservabilityResponseDTO>> search(
            @RequestParam(required = false) String conversationId,
            @RequestParam(required = false) String node,
            @RequestParam(required = false) String status) {
        return ResponseEntity.ok(llmObservabilityService.search(conversationId, node, status));
    }
}
