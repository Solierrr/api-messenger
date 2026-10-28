package com.solaria.messenger.core.usecase.admin;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import com.solaria.messenger.core.contract.response.AdminConversationPageDTO;
import com.solaria.messenger.core.contract.response.AdminConversationSummaryDTO;
import com.solaria.messenger.core.helper.AdminHistoryHelper;
import com.solaria.messenger.core.model.Conversation;
import com.solaria.messenger.core.model.LlmObservability;
import com.solaria.messenger.core.model.Message;
import com.solaria.messenger.core.model.enums.ConversationType;

import static com.solaria.messenger.core.mapper.AdminConversationMapper.toSummary;

/**
 * Caso de uso que lista as conversas com o chatbot no histórico administrativo.
 *
 * <p>
 * Busca as conversas {@code CHAT_BOT} iniciadas no período, da mais recente para a mais antiga. Sem datas, usa
 * os últimos 7 dias até hoje, em UTC. A busca varre no máximo 2000 conversas e marca a página como truncada
 * quando passa disso. Os filtros de sessão, usuário e texto aceitam trechos e não diferenciam maiúsculas. O
 * filtro de texto procura o trecho literal no conteúdo das mensagens, sem regex.
 * </p>
 *
 * <p>
 * <b>Depende de:</b>
 * </p>
 * <ul>
 *     <li>{@link MongoTemplate} -> busca as conversas do período.</li>
 *     <li>{@link AdminHistoryHelper} -> carrega mensagens, etapas e notas em lote.</li>
 * </ul>
 *
 * <p>
 * Principal operação disponibilizada:
 * </p>
 * <ul>
 *     <li>{@link #execute(LocalDate, LocalDate, String, String, String, String, Integer)} -> lista a página de
 *         conversas com os filtros informados.</li>
 * </ul>
 */
@Service
public class ListAdminConversationsUseCase {

    private static final int MAX_SCAN = 2000;
    private static final int DEFAULT_LIMIT = 200;
    private final MongoTemplate mongoTemplate;
    private final AdminHistoryHelper history;

    public ListAdminConversationsUseCase(MongoTemplate mongoTemplate, AdminHistoryHelper history) {
        this.mongoTemplate = mongoTemplate;
        this.history = history;
    }

    /**
     * Lista as conversas com o chatbot do período, com filtros opcionais.
     *
     * @param from primeiro dia do período. Sem valor, sete dias antes do fim.
     * @param to último dia do período, incluído. Sem valor, hoje em UTC.
     * @param assistant nome exato do assistente gravado na conversa.
     * @param sessionId trecho do identificador da conversa.
     * @param userId trecho do identificador do user que criou a conversa.
     * @param text trecho do conteúdo de alguma mensagem.
     * @param limit tamanho da página, entre 1 e 2000. Sem valor, usa 200.
     * @return página com os resumos, o total que passou nos filtros e a indicação de truncamento.
     */
    public AdminConversationPageDTO execute(LocalDate from, LocalDate to, String assistant, String sessionId,
            String userId, String text, Integer limit) {
        LocalDate end = to != null ? to : LocalDate.now(ZoneOffset.UTC);
        LocalDate start = from != null ? from : end.minusDays(7);
        Criteria criteria = Criteria.where("conversationType").is(ConversationType.CHAT_BOT)
                .and("startedAt").gte(start.atStartOfDay(ZoneOffset.UTC).toInstant())
                .lt(end.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant());
        if (isInformed(assistant)) {
            criteria = criteria.and("userDetails.assistant").is(assistant.trim());
        }

        // busca uma conversa a mais que o limite de varredura para saber se a página foi truncada
        Query query = Query.query(criteria).with(Sort.by(Sort.Direction.DESC, "startedAt")).limit(MAX_SCAN + 1);
        List<Conversation> found = mongoTemplate.find(query, Conversation.class);
        boolean truncated = found.size() > MAX_SCAN;
        List<Conversation> candidates = (truncated ? found.subList(0, MAX_SCAN) : found).stream()
                .filter(conversation -> contains(conversation.getId(), sessionId))
                .filter(conversation -> contains(String.valueOf(conversation.getCreatedBy()), userId))
                .toList();

        // o filtro de texto usa as mensagens já carregadas
        Set<String> ids = candidates.stream().map(Conversation::getId).collect(Collectors.toCollection(HashSet::new));
        Map<String, List<Message>> messages = history.messagesOf(ids);
        if (isInformed(text)) {
            Set<String> matching = idsWithText(messages, text.trim());
            candidates = candidates.stream().filter(conversation -> matching.contains(conversation.getId())).toList();
            ids = matching;
        }

        Map<String, List<LlmObservability>> steps = history.stepsOf(ids);
        Map<String, Integer> scores = history.scoresOf(ids);
        int size = Math.clamp(limit != null ? limit : DEFAULT_LIMIT, 1, MAX_SCAN);
        List<AdminConversationSummaryDTO> items = candidates.stream()
                .limit(size)
                .map(conversation -> toSummary(conversation, messages.getOrDefault(conversation.getId(), List.of()),
                        steps.getOrDefault(conversation.getId(), List.of()), scores.get(conversation.getId())))
                .toList();
        return new AdminConversationPageDTO(items, candidates.size(), truncated);
    }

    /** Conversas com alguma mensagem que contém o trecho, sem diferenciar maiúsculas */
    private static Set<String> idsWithText(Map<String, List<Message>> messages, String text) {
        String needle = text.toLowerCase();
        return messages.entrySet().stream()
                .filter(entry -> entry.getValue().stream()
                        .anyMatch(message -> message.getContent() != null
                                && message.getContent().toLowerCase().contains(needle)))
                .map(Map.Entry::getKey)
                .collect(Collectors.toSet());
    }

    private static boolean isInformed(String value) {
        return value != null && !value.isBlank();
    }

    /** Filtro vazio aceita tudo; senão o valor precisa conter o trecho, sem diferenciar maiúsculas */
    private static boolean contains(String value, String filter) {
        return !isInformed(filter) || (value != null && value.toLowerCase().contains(filter.trim().toLowerCase()));
    }
}
