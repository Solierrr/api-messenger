package com.solaria.messenger.core.usecase.observability;

import org.springframework.stereotype.Service;

import com.solaria.messenger.core.contract.response.LlmObservabilityResponseDTO;
import com.solaria.messenger.core.exception.ResourceNotFoundException;
import com.solaria.messenger.core.model.LlmObservability;
import com.solaria.messenger.core.repository.LlmObservabilityRepository;

import static com.solaria.messenger.core.mapper.LlmObservabilityMapper.toResponse;

@Service
public class GetLlmObservationUseCase {

    private final LlmObservabilityRepository llmObservabilityRepository;

    public GetLlmObservationUseCase(LlmObservabilityRepository llmObservabilityRepository) {
        this.llmObservabilityRepository = llmObservabilityRepository;
    }

    /** Consulta a etapa por ID usando o Repository da coleção de telemetria */
    public LlmObservabilityResponseDTO execute(String id) {
        LlmObservability observability = llmObservabilityRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Registro de observabilidade não encontrado com ID: " + id));
        return toResponse(observability);
    }
}
