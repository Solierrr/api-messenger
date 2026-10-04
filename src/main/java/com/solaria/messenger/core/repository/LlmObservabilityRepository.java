package com.solaria.messenger.core.repository;

import java.util.List;

import org.springframework.data.mongodb.repository.MongoRepository;

import com.solaria.messenger.core.model.LlmObservability;

/** Fronteira de persistência da telemetria LLM */
public interface LlmObservabilityRepository extends MongoRepository<LlmObservability, String> {

    /** Eventos de telemetria de uma conversa. */
    List<LlmObservability> findByConversationId(String conversationId);

    /** Eventos de telemetria de um nó do pipeline. */
    List<LlmObservability> findByNode(String node);

    /** Eventos de telemetria com o status informado */
    List<LlmObservability> findByStatus(String status);

    /** Os cem eventos mais recentes, usado quando nenhum filtro é informado */
    List<LlmObservability> findTop100ByOrderByTimestampDesc();
}
