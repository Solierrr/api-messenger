package com.solaria.messenger.service;

import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Service;

import com.solaria.messenger.dto.request.LlmObservabilityRequestDTO;
import com.solaria.messenger.dto.response.LlmObservabilityResponseDTO;
import com.solaria.messenger.exception.InvalidFieldException;
import com.solaria.messenger.exception.ResourceNotFoundException;
import com.solaria.messenger.model.LlmObservability;
import com.solaria.messenger.repository.LlmObservabilityRepository;

@Service
public class LlmObservabilityService {

    private final LlmObservabilityRepository llmObservabilityRepository;

    public LlmObservabilityService(LlmObservabilityRepository llmObservabilityRepository) {
        this.llmObservabilityRepository = llmObservabilityRepository;
    }

    public LlmObservabilityResponseDTO ingest(LlmObservabilityRequestDTO dto) {
        LlmObservability observability = new LlmObservability();
        observability.setNode(dto.getNode());
        observability.setStepOrder(dto.getStepOrder());
        observability.setStepType(dto.getStepType());
        observability.setModel(dto.getModel());
        observability.setConversationId(dto.getConversationId());
        observability.setTokensIn(dto.getTokensIn());
        observability.setTokensOut(dto.getTokensOut());
        observability.setTokensTotal(dto.getTokensTotal());
        observability.setLatencyMs(dto.getLatencyMs());
        observability.setStatus(dto.getStatus());
        observability.setError(dto.getError());
        observability.setTimestamp(dto.getTimestamp() != null ? dto.getTimestamp() : Instant.now());

        return toResponse(llmObservabilityRepository.save(observability));
    }

    public LlmObservabilityResponseDTO findById(String id) {
        LlmObservability observability = llmObservabilityRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Registro de observabilidade não encontrado com ID: " + id));
        return toResponse(observability);
    }


    public List<LlmObservabilityResponseDTO> search(String conversationId, String node, String status) {
        int informedFilterCount = countInformedFilters(conversationId, node, status);
        if (informedFilterCount > 1) {
            throw new InvalidFieldException(
                    "Apenas um filtro por chamada é suportado.");
        }

        List<LlmObservability> results;
        if (isInformed(conversationId)) {
            results = llmObservabilityRepository.findByConversationId(conversationId);
        } else if (isInformed(node)) {
            results = llmObservabilityRepository.findByNode(node);
        } else if (isInformed(status)) {
            results = llmObservabilityRepository.findByStatus(status);
        } else {
            results = llmObservabilityRepository.findAll();
        }

        return results.stream()
            .map(this::toResponse)
            .toList();
    }

    private int countInformedFilters(String conversationId, String node, String status) {
        int count = 0;
        if (isInformed(conversationId)) {
            count++;
        }
        if (isInformed(node)) {
            count++;
        }
        if (isInformed(status)) {
            count++;
        }
        return count;
    }

    private boolean isInformed(String value) {
        return value != null && !value.isBlank();
    }

    private LlmObservabilityResponseDTO toResponse(LlmObservability observability) {
        return LlmObservabilityResponseDTO.builder()
                .id(observability.getId())
                .node(observability.getNode())
                .stepOrder(observability.getStepOrder())
                .stepType(observability.getStepType())
                .model(observability.getModel())
                .conversationId(observability.getConversationId())
                .tokensIn(observability.getTokensIn())
                .tokensOut(observability.getTokensOut())
                .tokensTotal(observability.getTokensTotal())
                .latencyMs(observability.getLatencyMs())
                .status(observability.getStatus())
                .error(observability.getError())
                .timestamp(observability.getTimestamp())
                .build();
    }
}
