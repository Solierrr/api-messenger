package com.solaria.messenger.core.usecase.observability;

import java.time.Instant;

import org.springframework.stereotype.Service;

import com.solaria.messenger.core.contract.request.LlmObservabilityRequestDTO;
import com.solaria.messenger.core.contract.response.LlmObservabilityResponseDTO;
import com.solaria.messenger.core.model.LlmObservability;
import com.solaria.messenger.core.repository.LlmObservabilityRepository;

import static com.solaria.messenger.core.mapper.LlmObservabilityMapper.toResponse;

/**
 * Caso de uso que grava uma etapa de observabilidade do LLM.
 *
 * <p>
 * Quando o cliente não envia {@code timestamp}, usa o horário atual. Quando não envia {@code costUsd}, grava zero.
 * Cada chamada cria um registro novo.
 * </p>
 *
 * <p>
 * <b>Depende de:</b>
 * </p>
 * <ul>
 *     <li>{@link LlmObservabilityRepository} -> grava o registro.</li>
 * </ul>
 *
 * <p>
 * Principal operação disponibilizada:
 * </p>
 * <ul>
 *     <li>{@link #execute(LlmObservabilityRequestDTO)} -> grava a nova etapa.</li>
 * </ul>
 */
@Service
public class IngestLlmObservationUseCase {

    private final LlmObservabilityRepository llmObservabilityRepository;

    public IngestLlmObservationUseCase(LlmObservabilityRepository llmObservabilityRepository) {
        this.llmObservabilityRepository = llmObservabilityRepository;
    }

    /**
     * Grava uma nova etapa de observabilidade.
     *
     * @param dto dados da etapa, como nó, tokens, latência e status.
     * @return registro gravado.
     */
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
