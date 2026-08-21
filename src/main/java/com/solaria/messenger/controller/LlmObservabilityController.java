package com.solaria.messenger.controller;

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

import com.solaria.messenger.dto.request.LlmObservabilityRequestDTO;
import com.solaria.messenger.dto.response.LlmObservabilityResponseDTO;
import com.solaria.messenger.openapi.LlmObservabilityOpenApi;
import com.solaria.messenger.service.LlmObservabilityService;

import jakarta.validation.Valid;


@RestController
@RequestMapping("/internal/observability")
public class LlmObservabilityController implements LlmObservabilityOpenApi {

    private final LlmObservabilityService llmObservabilityService;

    public LlmObservabilityController(LlmObservabilityService llmObservabilityService) {
        this.llmObservabilityService = llmObservabilityService;
    }

    @Override
    @PostMapping
    public ResponseEntity<LlmObservabilityResponseDTO> ingest(@Valid @RequestBody LlmObservabilityRequestDTO dto) {
        LlmObservabilityResponseDTO response = llmObservabilityService.ingest(dto);
        return ResponseEntity.created(URI.create("/internal/observability/" + response.getId())).body(response);
    }

    @Override
    @GetMapping("/{id}")
    public ResponseEntity<LlmObservabilityResponseDTO> findById(@PathVariable String id) {
        return ResponseEntity.ok(llmObservabilityService.findById(id));
    }

    @Override
    @GetMapping
    public ResponseEntity<List<LlmObservabilityResponseDTO>> search(
            @RequestParam(required = false) String conversationId,
            @RequestParam(required = false) String node,
            @RequestParam(required = false) String status) {
        return ResponseEntity.ok(llmObservabilityService.search(conversationId, node, status));
    }
}
