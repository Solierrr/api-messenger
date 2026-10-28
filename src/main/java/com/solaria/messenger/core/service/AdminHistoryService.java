package com.solaria.messenger.core.service;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.data.domain.Sort;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Criteria;
import org.springframework.data.mongodb.core.query.Query;
import org.springframework.stereotype.Service;

import com.solaria.messenger.core.contract.response.AdminConversationDetailDTO;
import com.solaria.messenger.core.contract.response.AdminConversationPageDTO;
import com.solaria.messenger.core.contract.response.AdminConversationSummaryDTO;
import com.solaria.messenger.core.contract.response.AdminMessageDTO;
import com.solaria.messenger.core.contract.response.LlmObservabilityResponseDTO;
import com.solaria.messenger.core.exception.ResourceNotFoundException;
import com.solaria.messenger.core.mapper.LlmObservabilityMapper;
import com.solaria.messenger.core.model.Conversation;
import com.solaria.messenger.core.model.LlmObservability;
import com.solaria.messenger.core.model.Message;
import com.solaria.messenger.core.model.MessageMetadata;
import com.solaria.messenger.core.model.Nps;
import com.solaria.messenger.core.model.enums.ConversationType;

@Service
public class AdminHistoryService {

    static final int MAX_SCAN = 2000;
    static final int DEFAULT_LIMIT = 200;
    static final String UNKNOWN_ASSISTANT = "desconhecido";
    private static final String ROLE_USER = "user";
    private static final String ROLE_ASSISTANT = "assistant";
    private static final String KEY_ASSISTANT = "assistant";
    private static final String STATUS_OK = "ok";
    private static final String CONVERSATION_ID = "conversationId";

    private final MongoTemplate mongoTemplate;

    public AdminHistoryService(MongoTemplate mongoTemplate) {
        this.mongoTemplate = mongoTemplate;
    }

    public AdminConversationPageDTO list(LocalDate from, LocalDate to, String assistant, String sessionId,
            String userId, String text, Integer limit) {
        LocalDate end = to != null ? to : LocalDate.now(ZoneOffset.UTC);
        LocalDate start = from != null ? from : end.minusDays(7);
        Criteria criteria = Criteria.where("conversationType").is(ConversationType.CHAT_BOT)
                .and("startedAt").gte(start.atStartOfDay(ZoneOffset.UTC).toInstant())
                .lt(end.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant());
        if (isInformed(assistant)) {
            criteria = criteria.and("userDetails.assistant").is(assistant.trim());
        }
        Query query = Query.query(criteria).with(Sort.by(Sort.Direction.DESC, "startedAt")).limit(MAX_SCAN + 1);
        List<Conversation> found = mongoTemplate.find(query, Conversation.class);
        boolean truncated = found.size() > MAX_SCAN;
        List<Conversation> candidates = (truncated ? found.subList(0, MAX_SCAN) : found).stream()
                .filter(conversation -> contains(conversation.getId(), sessionId))
                .filter(conversation -> contains(String.valueOf(conversation.getCreatedBy()), userId))
                .toList();

        Set<String> ids = candidates.stream().map(Conversation::getId).collect(Collectors.toCollection(HashSet::new));
        Map<String, List<Message>> messages = messagesOf(ids);
        if (isInformed(text)) {
            Set<String> matching = idsWithText(messages, text.trim());
            candidates = candidates.stream().filter(conversation -> matching.contains(conversation.getId())).toList();
            ids = matching;
        }
        Map<String, List<LlmObservability>> steps = stepsOf(ids);
        Map<String, Integer> scores = scoresOf(ids);

        int size = Math.clamp(limit != null ? limit : DEFAULT_LIMIT, 1, MAX_SCAN);
        List<AdminConversationSummaryDTO> items = candidates.stream()
                .limit(size)
                .map(conversation -> summarize(conversation, messages.getOrDefault(conversation.getId(), List.of()),
                        steps.getOrDefault(conversation.getId(), List.of()), scores.get(conversation.getId())))
                .toList();
        return new AdminConversationPageDTO(items, candidates.size(), truncated);
    }

    public AdminConversationDetailDTO detail(String id) {
        Conversation conversation = mongoTemplate.findById(id, Conversation.class);
        if (conversation == null || conversation.getConversationType() != ConversationType.CHAT_BOT) {
            throw new ResourceNotFoundException("Conversa não encontrada com ID: " + id);
        }
        List<Message> messages = messagesOf(Set.of(id)).getOrDefault(id, List.of());
        List<LlmObservability> steps = stepsOf(Set.of(id)).getOrDefault(id, List.of());
        Integer score = scoresOf(Set.of(id)).get(id);

        List<AdminMessageDTO> result = new ArrayList<>();
        Instant previous = null;
        int turn = 0;
        for (Message message : messages) {
            if (!ROLE_ASSISTANT.equals(message.getRole())) {
                result.add(userMessage(message));
                continue;
            }
            turn++;
            List<LlmObservabilityResponseDTO> slice = stepsBetween(steps, previous, message.getTimestamp());
            previous = message.getTimestamp();
            result.add(assistantMessage(message, turn, slice));
        }
        Map<String, Object> details = detailsOf(conversation);
        Object thread = details.get("threadId");
        return new AdminConversationDetailDTO(summarize(conversation, messages, steps, score),
                thread != null ? thread.toString() : null, details, result);
    }

    private static AdminMessageDTO userMessage(Message message) {
        return new AdminMessageDTO(message.getRole(), message.getContent(), message.getTimestamp(),
                null, null, null, List.of(), List.of(), 0, 0, List.of());
    }

    private static AdminMessageDTO assistantMessage(Message message, int turn,
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

    private static Map<String, Object> detailsOf(Conversation conversation) {
        return conversation.getUserDetails() != null ? conversation.getUserDetails() : Map.of();
    }

    private List<LlmObservabilityResponseDTO> stepsBetween(List<LlmObservability> steps, Instant after,
            Instant until) {
        return steps.stream()
                .filter(step -> step.getTimestamp() != null
                        && (after == null || step.getTimestamp().isAfter(after))
                        && !step.getTimestamp().isAfter(until))
                .map(LlmObservabilityMapper::toResponse)
                .toList();
    }

    private AdminConversationSummaryDTO summarize(Conversation conversation, List<Message> messages,
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

    private Map<String, List<Message>> messagesOf(Collection<String> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        Query query = Query.query(Criteria.where(CONVERSATION_ID).in(ids).and("deleted").ne(true))
                .with(Sort.by(Sort.Direction.ASC, "sequence", "timestamp"));
        return mongoTemplate.find(query, Message.class).stream()
                .collect(Collectors.groupingBy(Message::getConversationId, LinkedHashMap::new, Collectors.toList()));
    }

    private Map<String, List<LlmObservability>> stepsOf(Collection<String> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        Query query = Query.query(Criteria.where(CONVERSATION_ID).in(ids))
                .with(Sort.by(Sort.Direction.ASC, "timestamp", "stepOrder"));
        return mongoTemplate.find(query, LlmObservability.class).stream()
                .collect(Collectors.groupingBy(LlmObservability::getConversationId, LinkedHashMap::new,
                        Collectors.toList()));
    }

    private Map<String, Integer> scoresOf(Collection<String> ids) {
        if (ids.isEmpty()) {
            return Map.of();
        }
        return mongoTemplate.find(Query.query(Criteria.where(CONVERSATION_ID).in(ids)), Nps.class).stream()
                .collect(Collectors.toMap(Nps::getConversationId, Nps::getScore, (a, b) -> a));
    }

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

    private static boolean contains(String value, String filter) {
        return !isInformed(filter) || (value != null && value.toLowerCase().contains(filter.trim().toLowerCase()));
    }

    private static double orZero(Double value) {
        return value != null ? value : 0.0;
    }
}
