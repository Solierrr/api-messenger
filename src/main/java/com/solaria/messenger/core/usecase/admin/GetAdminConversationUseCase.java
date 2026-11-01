package com.solaria.messenger.core.usecase.admin;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;

import com.solaria.messenger.core.contract.response.AdminConversationDetailDTO;
import com.solaria.messenger.core.contract.response.AdminMessageDTO;
import com.solaria.messenger.core.contract.response.LlmObservabilityResponseDTO;
import com.solaria.messenger.core.exception.ResourceNotFoundException;
import com.solaria.messenger.core.helper.AdminHistoryHelper;
import com.solaria.messenger.core.mapper.LlmObservabilityMapper;
import com.solaria.messenger.core.model.Conversation;
import com.solaria.messenger.core.model.LlmObservability;
import com.solaria.messenger.core.model.Message;
import com.solaria.messenger.core.model.enums.ConversationType;

import static com.solaria.messenger.core.mapper.AdminConversationMapper.detailsOf;
import static com.solaria.messenger.core.mapper.AdminConversationMapper.toAssistantMessage;
import static com.solaria.messenger.core.mapper.AdminConversationMapper.toSummary;
import static com.solaria.messenger.core.mapper.AdminConversationMapper.toUserMessage;

/**
 * Caso de uso que devolve o detalhe de uma conversa com o chatbot no histórico administrativo.
 *
 * <p>
 * Traz o resumo, o thread gravado nos detalhes e as mensagens em ordem. Cada resposta do assistente recebe o
 * número do turno e as etapas do LLM gravadas desde a resposta anterior. Conversa que não é {@code CHAT_BOT}
 * é tratada como inexistente.
 * </p>
 *
 * <p>
 * <b>Depende de:</b>
 * </p>
 * <ul>
 *     <li>{@link MongoTemplate} -> carrega a conversa.</li>
 *     <li>{@link AdminHistoryHelper} -> carrega mensagens, etapas e nota da conversa.</li>
 * </ul>
 *
 * <p>
 * Principal operação disponibilizada:
 * </p>
 * <ul>
 *     <li>{@link #execute(String)} -> devolve o detalhe da conversa.</li>
 * </ul>
 */
@Service
public class GetAdminConversationUseCase {

    private static final String ROLE_ASSISTANT = "assistant";
    private final MongoTemplate mongoTemplate;
    private final AdminHistoryHelper history;

    public GetAdminConversationUseCase(MongoTemplate mongoTemplate, AdminHistoryHelper history) {
        this.mongoTemplate = mongoTemplate;
        this.history = history;
    }

    /**
     * Devolve o detalhe de uma conversa com o chatbot.
     *
     * @param id identificador da conversa.
     * @return resumo, thread, detalhes e mensagens da conversa.
     * @throws ResourceNotFoundException caso a conversa não exista ou não seja do tipo {@code CHAT_BOT}.
     */
    public AdminConversationDetailDTO execute(String id) {
        Conversation conversation = mongoTemplate.findById(id, Conversation.class);
        if (conversation == null || conversation.getConversationType() != ConversationType.CHAT_BOT) {
            throw new ResourceNotFoundException("Conversa não encontrada com ID: " + id);
        }
        List<Message> messages = history.messagesOf(Set.of(id)).getOrDefault(id, List.of());
        List<LlmObservability> steps = history.stepsOf(Set.of(id)).getOrDefault(id, List.of());
        Integer score = history.scoresOf(Set.of(id)).get(id);

        // cada resposta do assistente leva as etapas gravadas desde a resposta anterior
        List<AdminMessageDTO> result = new ArrayList<>();
        Instant previous = null;
        int turn = 0;
        for (Message message : messages) {
            if (!ROLE_ASSISTANT.equals(message.getRole())) {
                result.add(toUserMessage(message));
                continue;
            }
            turn++;
            List<LlmObservabilityResponseDTO> slice = stepsBetween(steps, previous, message.getTimestamp());
            previous = message.getTimestamp();
            result.add(toAssistantMessage(message, turn, slice));
        }

        Map<String, Object> details = detailsOf(conversation);
        Object thread = details.get("threadId");
        return new AdminConversationDetailDTO(toSummary(conversation, messages, steps, score),
                thread != null ? thread.toString() : null, details, result);
    }

    /** Etapas depois de {@code after} (exclusivo) até {@code until} (inclusivo); sem {@code after}, desde o início */
    private static List<LlmObservabilityResponseDTO> stepsBetween(List<LlmObservability> steps, Instant after,
            Instant until) {
        return steps.stream()
                .filter(step -> step.getTimestamp() != null
                        && (after == null || step.getTimestamp().isAfter(after))
                        && !step.getTimestamp().isAfter(until))
                .map(LlmObservabilityMapper::toResponse)
                .toList();
    }
}
