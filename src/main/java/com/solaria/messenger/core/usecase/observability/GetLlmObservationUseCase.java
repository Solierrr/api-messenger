package com.solaria.messenger.core.usecase.observability;

import org.springframework.stereotype.Service;

import com.solaria.messenger.core.contract.response.LlmObservabilityResponseDTO;
import com.solaria.messenger.core.exception.ResourceNotFoundException;
import com.solaria.messenger.core.model.LlmObservability;
import com.solaria.messenger.core.repository.LlmObservabilityRepository;

import static com.solaria.messenger.core.mapper.LlmObservabilityMapper.toResponse;

/**
 * Caso de uso que consulta um registro de observabilidade de uma etapa do LLM.
 *
 * <p>
 * Lê direto da coleção de telemetria e não altera o registro.
 * </p>
 *
 * <p>
 * <b>Depende de:</b>
 * </p>
 * <ul>
 *     <li>{@link LlmObservabilityRepository} -> busca o registro pelo identificador.</li>
 * </ul>
 *
 * <p>
 * Principal operação disponibilizada:
 * </p>
 * <ul>
 *     <li>{@link #execute(String)} -> busca a etapa pelo identificador.</li>
 * </ul>
 */
@Service
public class GetLlmObservationUseCase {

    private final LlmObservabilityRepository llmObservabilityRepository;

    public GetLlmObservationUseCase(LlmObservabilityRepository llmObservabilityRepository) {
        this.llmObservabilityRepository = llmObservabilityRepository;
    }

    /**
     * Busca a etapa de observabilidade pelo identificador.
     *
     * @param id identificador do registro.
     * @return registro encontrado.
     * @throws ResourceNotFoundException caso o registro não exista.
     */
    public LlmObservabilityResponseDTO execute(String id) {
        LlmObservability observability = llmObservabilityRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Registro de observabilidade não encontrado com ID: " + id));
        return toResponse(observability);
    }
}
