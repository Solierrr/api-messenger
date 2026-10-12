package com.solaria.messenger.core.service;

import java.time.Instant;
import java.util.List;

import org.springframework.stereotype.Service;

import com.solaria.messenger.core.contract.request.LlmObservabilityRequestDTO;
import com.solaria.messenger.core.contract.response.LlmObservabilityResponseDTO;
import com.solaria.messenger.core.exception.InvalidFieldException;
import com.solaria.messenger.core.exception.ResourceNotFoundException;
import com.solaria.messenger.core.model.LlmObservability;
import com.solaria.messenger.core.repository.LlmObservabilityRepository;
import com.solaria.messenger.core.mapper.LlmObservabilityMapper;

import static com.solaria.messenger.core.mapper.LlmObservabilityMapper.toResponse;

/** Mantém a telemetria do pipeline LLM isolada da mensageria, sem recalcular custos ou tokens recebidos */
@Service
public class LlmObservabilityService {

    private final LlmObservabilityRepository llmObservabilityRepository;

    public LlmObservabilityService(LlmObservabilityRepository llmObservabilityRepository) {
        this.llmObservabilityRepository = llmObservabilityRepository;
    }

    /** Persiste uma nova etapa e preserva os defaults de timestamp e custo quando omitidos. */
    public LlmObservabilityResponseDTO ingest(LlmObservabilityRequestDTO dto) {
        LlmObservability observability = new LlmObservability();
        observability.setNode(dto.getNode());
        observability.setStepOrder(dto.getStepOrder());
        observability.setStepType(dto.getStepType());
        observability.setModel(dto.getModel());
        observability.setConversationId(dto.getConversationId());
        observability.setEnvironment(dto.getEnvironment());
        observability.setTokensIn(dto.getTokensIn());
        observability.setTokensOut(dto.getTokensOut());
        observability.setTokensTotal(dto.getTokensTotal());
        observability.setLatencyMs(dto.getLatencyMs());
        observability.setStatus(dto.getStatus());
        observability.setError(dto.getError());
        observability.setTimestamp(dto.getTimestamp() != null ? dto.getTimestamp() : Instant.now());
        observability.setCostUsd(dto.getCostUsd() != null ? dto.getCostUsd() : 0.0);

        return toResponse(llmObservabilityRepository.save(observability));
    }

    /** Consulta a etapa por ID usando o Repository da coleção de telemetria */
    public LlmObservabilityResponseDTO findById(String id) {
        LlmObservability observability = llmObservabilityRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Registro de observabilidade não encontrado com ID: " + id));
        return toResponse(observability);
    }

    /** Aplica no máximo um filtro | sem filtro retorna os cem eventos mais recentes */
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
            results = llmObservabilityRepository.findTop100ByOrderByTimestampDesc();
        }

        return results.stream()
            .map(LlmObservabilityMapper::toResponse)
            .toList();
    }

    /** Conta quantos filtros de busca foram preenchido */
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

    /** Filtro preenchido é não nulo e não vazio */
    private boolean isInformed(String value) {
        return value != null && !value.isBlank();
    }
}
