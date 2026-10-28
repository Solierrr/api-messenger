package com.solaria.messenger.core.helper;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Component;

import com.solaria.messenger.core.model.LlmObservability;
import com.solaria.messenger.core.model.Message;
import com.solaria.messenger.core.model.Nps;

/**
 * Carrega em lote as mensagens, as etapas do LLM e as notas de NPS do histórico administrativo.
 *
 * <p>
 * Cada consulta recebe um conjunto de conversas e devolve os dados agrupados por conversa, para os use cases do
 * histórico não consultarem uma conversa por vez. Conjunto vazio não consulta o banco.
 * </p>
 *
 * <p>
 * <b>Depende de:</b>
 * </p>
 * <ul>
 *     <li>{@link MongoTemplate} -> consulta as coleções de mensagens, telemetria e NPS.</li>
 * </ul>
 *
 * <p>
 * Principais operações disponibilizadas:
 * </p>
 * <ul>
 *     <li>{@link #messagesOf(Collection)} -> mensagens não excluídas, em ordem de sequência.</li>
 *     <li>{@link #stepsOf(Collection)} -> etapas do LLM, em ordem de horário.</li>
 *     <li>{@link #scoresOf(Collection)} -> nota de NPS de cada conversa.</li>
 * </ul>
 */
@Component
public class AdminHistoryHelper {

    private static final String CONVERSATION_ID = "conversationId";
    private final MongoTemplate mongoTemplate;

    public AdminHistoryHelper(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    /**
     * Carrega as mensagens não excluídas das conversas, por sequência e horário.
     *
     * @param ids identificadores das conversas.
     * @return mapa de id da conversa para as mensagens em ordem.
     */
    public Map<String, List<Message>> messagesOf(Collection<String> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        Query query = Query.query(Criteria.where(CONVERSATION_ID).in(ids).and("deleted").ne(true))
                .with(Sort.by(Sort.Direction.ASC, "sequence", "timestamp"));
        return mongoTemplate.find(query, Message.class).stream()
                .collect(Collectors.groupingBy(Message::getConversationId, LinkedHashMap::new, Collectors.toList()));
    }

    /**
     * Carrega as etapas do LLM das conversas, por horário e ordem da etapa.
     *
     * @param ids identificadores das conversas.
     * @return mapa de id da conversa para as etapas em ordem.
     */
    public Map<String, List<LlmObservability>> stepsOf(Collection<String> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        Query query = Query.query(Criteria.where(CONVERSATION_ID).in(ids))
                .with(Sort.by(Sort.Direction.ASC, "timestamp", "stepOrder"));
        return mongoTemplate.find(query, LlmObservability.class).stream()
                .collect(Collectors.groupingBy(LlmObservability::getConversationId, LinkedHashMap::new,
                        Collectors.toList()));
    }

    /**
     * Carrega a nota de NPS de cada conversa.
     *
     * @param ids identificadores das conversas.
     * @return mapa de id da conversa para a nota. Conversa sem NPS não aparece.
     */
    public Map<String, Integer> scoresOf(Collection<String> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return mongoTemplate.find(Query.query(Criteria.where(CONVERSATION_ID).in(ids)), Nps.class).stream()
                .collect(Collectors.toMap(Nps::getConversationId, Nps::getScore, (a, b) -> a));
    }
}
