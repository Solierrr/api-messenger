package com.solaria.messenger.core.usecase.observability;

import java.util.List;

import org.springframework.stereotype.Service;

import com.solaria.messenger.core.contract.response.LlmObservabilityResponseDTO;
import com.solaria.messenger.core.exception.InvalidFieldException;
import com.solaria.messenger.core.mapper.LlmObservabilityMapper;
import com.solaria.messenger.core.model.LlmObservability;
import com.solaria.messenger.core.repository.LlmObservabilityRepository;

/**
 * Caso de uso que busca etapas de observabilidade do LLM.
 *
 * <p>
 * Aceita no máximo um filtro por chamada: {@code conversationId}, {@code node} ou {@code status}. Sem filtro, traz
 * os 100 registros mais recentes pelo horário. Filtro vazio ou em branco conta como não informado.
 * </p>
 *
 * <p>
 * <b>Depende de:</b>
 * </p>
 * <ul>
 *     <li>{@link LlmObservabilityRepository} -> busca os registros.</li>
 * </ul>
 *
 * <p>
 * Principal operação disponibilizada:
 * </p>
 * <ul>
 *     <li>{@link #execute(String, String, String)} -> busca as etapas pelo filtro informado.</li>
 * </ul>
 */
@Service
public class SearchLlmObservationsUseCase {

    private final LlmObservabilityRepository llmObservabilityRepository;

    public SearchLlmObservationsUseCase(LlmObservabilityRepository llmObservabilityRepository) {
        this.llmObservabilityRepository = llmObservabilityRepository;
    }

    /**
     * Busca as etapas de observabilidade pelo filtro informado.
     *
     * @param conversationId filtro pelo identificador da conversa.
     * @param node filtro pelo nó do pipeline.
     * @param status filtro pelo status da etapa.
     * @return registros encontrados, no formato de resposta.
     * @throws InvalidFieldException caso mais de um filtro seja informado.
     */
    public List<LlmObservabilityResponseDTO> execute(String conversationId, String node, String status) {
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
