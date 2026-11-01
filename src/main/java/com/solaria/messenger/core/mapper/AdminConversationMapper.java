package com.solaria.messenger.core.mapper;

import java.util.List;
import java.util.Map;

import com.solaria.messenger.core.contract.response.AdminConversationSummaryDTO;
import com.solaria.messenger.core.contract.response.AdminMessageDTO;
import com.solaria.messenger.core.contract.response.LlmObservabilityResponseDTO;
import com.solaria.messenger.core.model.Conversation;
import com.solaria.messenger.core.model.LlmObservability;
import com.solaria.messenger.core.model.Message;
import com.solaria.messenger.core.model.MessageMetadata;

/**
 * Mapeamento das conversas com o chatbot para os contratos do histórico administrativo.
 *
 * <p>
 * Monta o resumo da conversa e os itens de mensagem a partir de dados já carregados, sem consultas nem
 * autorização. Custo e latência somam as etapas do LLM, e valor ausente conta como zero.
 * </p>
 *
 * <p>
 * Principais operações disponibilizadas:
 * </p>
 * <ul>
 *     <li>{@link #toSummary(Conversation, List, List, Integer)} -> monta o resumo da conversa.</li>
 *     <li>{@link #toUserMessage(Message)} -> monta o item de uma mensagem do user.</li>
 *     <li>{@link #toAssistantMessage(Message, int, List)} -> monta o item de uma resposta do assistente.</li>
 *     <li>{@link #detailsOf(Conversation)} -> devolve os detalhes da conversa, ou um mapa vazio.</li>
 * </ul>
 */
public final class AdminConversationMapper {

    /** Nome usado quando a conversa não gravou o assistente */
    public static final String UNKNOWN_ASSISTANT = "desconhecido";
    private static final String ROLE_USER = "user";
    private static final String KEY_ASSISTANT = "assistant";
    private static final String STATUS_OK = "ok";

    private AdminConversationMapper() { }

    /**
     * Monta o resumo de uma conversa com o chatbot.
     *
     * <p>
     * A primeira mensagem é a primeira do user. Há erro quando alguma etapa tem status diferente de {@code ok}.
     * </p>
     *
     * @param conversation conversa resumida.
     * @param messages mensagens da conversa, em ordem.
     * @param steps etapas do LLM da conversa.
     * @param score nota do NPS, ou nulo quando a conversa não foi avaliada.
     * @return resumo com assistente, origem, contagem de mensagens, NPS, custo, latência e erro.
     */
    public static AdminConversationSummaryDTO toSummary(Conversation conversation, List<Message> messages,
            List<LlmObservability> steps, Integer score) {
        Map<String, Object> details = detailsOf(conversation);
        Object assistant = details.get(KEY_ASSISTANT);
        Object source = details.get("source");
        String first = messages.stream()
                .filter(message -> ROLE_USER.equals(message.getRole()))
                .map(Message::getContent)
                .findFirst()
                .orElse("");
        return new AdminConversationSummaryDTO(
                conversation.getId(),
                conversation.getCreatedBy(),
                assistant != null ? assistant.toString() : UNKNOWN_ASSISTANT,
                source != null ? source.toString() : null,
                conversation.getStartedAt(),
                messages.size(),
                first,
                score,
                steps.stream().mapToDouble(step -> orZero(step.getCostUsd())).sum(),
                steps.stream().mapToDouble(step -> orZero(step.getLatencyMs())).sum(),
                steps.stream().anyMatch(step -> step.getStatus() != null && !STATUS_OK.equals(step.getStatus())));
    }

    /**
     * Monta o item de uma mensagem do user, sem dados de turno nem etapas.
     *
     * @param message mensagem do user.
     * @return item da mensagem para o detalhe.
     */
    public static AdminMessageDTO toUserMessage(Message message) {
        return new AdminMessageDTO(message.getRole(), message.getContent(), message.getTimestamp(),
                null, null, null, List.of(), List.of(), 0, 0, List.of());
    }

    /**
     * Monta o item de uma resposta do assistente com o turno e as etapas que a produziram.
     *
     * @param message resposta do assistente.
     * @param turn número da resposta dentro da conversa, a partir de 1.
     * @param slice etapas do LLM gravadas para esta resposta.
     * @return item com especialistas, passos do workflow, custo e latência somados das etapas.
     */
    public static AdminMessageDTO toAssistantMessage(Message message, int turn,
            List<LlmObservabilityResponseDTO> slice) {
        MessageMetadata metadata = message.getMetadata() != null ? message.getMetadata() : new MessageMetadata();
        List<String> specialists = metadata.getSpecialistsUsed() != null ? metadata.getSpecialistsUsed() : List.of();
        List<String> workflowSteps = metadata.getWorkflowSteps() != null ? metadata.getWorkflowSteps() : List.of();
        return new AdminMessageDTO(message.getRole(), message.getContent(), message.getTimestamp(), turn,
                metadata.getThreadTurn(), metadata.getTurnId(), specialists, workflowSteps,
                slice.stream().mapToDouble(step -> orZero(step.getLatencyMs())).sum(),
                slice.stream().mapToDouble(step -> orZero(step.getCostUsd())).sum(),
                slice);
    }

    /**
     * Devolve os detalhes gravados na conversa.
     *
     * @param conversation conversa consultada.
     * @return detalhes da conversa, ou um mapa vazio quando não há detalhes.
     */
    public static Map<String, Object> detailsOf(Conversation conversation) {
        return conversation.getUserDetails() != null ? conversation.getUserDetails() : Map.of();
    }

    private static double orZero(Double value) {
        return value != null ? value : 0.0;
    }
}
