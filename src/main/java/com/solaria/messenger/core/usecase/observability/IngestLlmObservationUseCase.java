package com.solaria.messenger.core.usecase.observability;

import java.time.Instant;

import org.springframework.stereotype.Service;

import com.solaria.messenger.core.contract.request.LlmObservabilityRequestDTO;
import com.solaria.messenger.core.contract.response.LlmObservabilityResponseDTO;
import com.solaria.messenger.core.model.LlmObservability;
import com.solaria.messenger.core.repository.LlmObservabilityRepository;

import static com.solaria.messenger.core.mapper.LlmObservabilityMapper.toResponse;

@Service
public class IngestLlmObservationUseCase {

    private final LlmObservabilityRepository llmObservabilityRepository;

    public IngestLlmObservationUseCase(LlmObservabilityRepository llmObservabilityRepository) {
        this.llmObservabilityRepository = llmObservabilityRepository;
    }

    /** Persiste uma nova etapa e preserva os defaults de timestamp e custo quando omitidos. */
    public LlmObservabilityResponseDTO execute(LlmObservabilityRequestDTO dto) {
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
}
