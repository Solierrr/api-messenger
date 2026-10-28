package com.solaria.messenger.core.usecase.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.data.mongodb.core.query.Query;

import com.solaria.messenger.core.contract.response.AdminConversationDetailDTO;
import com.solaria.messenger.core.contract.response.AdminConversationPageDTO;
import com.solaria.messenger.core.exception.ResourceNotFoundException;
import com.solaria.messenger.core.helper.AdminHistoryHelper;
import com.solaria.messenger.core.mapper.AdminConversationMapper;
import com.solaria.messenger.core.model.Conversation;
import com.solaria.messenger.core.model.LlmObservability;
import com.solaria.messenger.core.model.Message;
import com.solaria.messenger.core.model.MessageMetadata;
import com.solaria.messenger.core.model.Nps;
import com.solaria.messenger.core.model.enums.ConversationType;
import com.solaria.messenger.core.model.enums.ObservabilityStepType;

@ExtendWith(MockitoExtension.class)
class AdminHistoryUseCasesTests {

    private static final Instant T0 = Instant.parse("2026-10-20T10:00:00Z");
    private static final UUID USER = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Mock
    private MongoTemplate mongoTemplate;

    private ListAdminConversationsUseCase listConversations;
    private GetAdminConversationUseCase getConversation;

    @BeforeEach
    void setUp() {
        AdminHistoryHelper history = new AdminHistoryHelper(mongoTemplate);
        listConversations = new ListAdminConversationsUseCase(mongoTemplate, history);
        getConversation = new GetAdminConversationUseCase(mongoTemplate, history);
    }

    @Test
    void listaResumoComCustoLatenciaNpsEErro() {
        givenConversation("c1", Map.of("assistant", "assistant", "source", "debug"));
        givenMessages(List.of(
                message("c1", "user", "oi, preciso de um eletricista", 1, T0),
                message("c1", "assistant", "claro", 2, T0.plusSeconds(5))));
        givenSteps(List.of(
                step("c1", "router", 1, "ok", 100.0, 0.001, T0.plusSeconds(1)),
                step("c1", "faq_reader", 2, "timeout", 250.0, 0.002, T0.plusSeconds(3))));
        given(mongoTemplate.find(any(Query.class), eq(Nps.class))).willReturn(List.of(nps("c1", 9)));

        AdminConversationPageDTO page = listConversations.execute(LocalDate.of(2026, 10, 20),
                LocalDate.of(2026, 10, 21), null, null, null, null, null);

        assertThat(page.total()).isEqualTo(1);
        assertThat(page.truncated()).isFalse();
        var item = page.items().getFirst();
        assertThat(item.sessionId()).isEqualTo("c1");
        assertThat(item.assistant()).isEqualTo("assistant");
        assertThat(item.source()).isEqualTo("debug");
        assertThat(item.messageCount()).isEqualTo(2);
        assertThat(item.firstMessage()).isEqualTo("oi, preciso de um eletricista");
        assertThat(item.nps()).isEqualTo(9);
        assertThat(item.costUsd()).isCloseTo(0.003, within(1e-9));
        assertThat(item.latencyMs()).isEqualTo(350.0);
        assertThat(item.error()).isTrue();
    }

    @Test
    void conversaSemAssistenteGravadoVoltaComoDesconhecida() {
        givenConversation("c1", null);
        givenMessages(List.of(message("c1", "user", "oi", 1, T0)));
        givenSteps(List.of());
        given(mongoTemplate.find(any(Query.class), eq(Nps.class))).willReturn(List.of());

        var item = listConversations.execute(null, null, null, null, null, null, null).items().getFirst();

        assertThat(item.assistant()).isEqualTo(AdminConversationMapper.UNKNOWN_ASSISTANT);
        assertThat(item.nps()).isNull();
        assertThat(item.error()).isFalse();
    }

    @Test
    void filtraPorTrechoDoSessionIdEDoUsuario() {
        givenConversation("abc123", Map.of("assistant", "assistant"));
        givenMessages(List.of(message("abc123", "user", "oi", 1, T0)));
        givenSteps(List.of());
        given(mongoTemplate.find(any(Query.class), eq(Nps.class))).willReturn(List.of());

        assertThat(listConversations.execute(null, null, null, "C12", null, null, null).items()).hasSize(1);
        assertThat(listConversations.execute(null, null, null, "zzz", null, null, null).items()).isEmpty();
        assertThat(listConversations.execute(null, null, null, null, "1111", null, null).items()).hasSize(1);
        assertThat(listConversations.execute(null, null, null, null, "9999", null, null).items()).isEmpty();
    }

    @Test
    void filtraPeloTextoDasMensagensSemDistinguirCaixaNemInterpretarRegex() {
        givenConversation("c1", Map.of("assistant", "assistant"));
        givenMessages(List.of(message("c1", "user", "Preciso de um eletricista (urgente)", 1, T0)));
        givenSteps(List.of());
        given(mongoTemplate.find(any(Query.class), eq(Nps.class))).willReturn(List.of());

        assertThat(listConversations.execute(null, null, null, null, null, "ELETRICISTA", null).items()).hasSize(1);
        assertThat(listConversations.execute(null, null, null, null, null, "(urgente)", null).items()).hasSize(1);
        assertThat(listConversations.execute(null, null, null, null, null, ".*", null).items()).isEmpty();
    }

    @Test
    void detalheSeparaOsPassosDeCadaResposta() {
        Conversation conversation = conversation("c1", Map.of("assistant", "assistant", "threadId", "debug:u:abc"));
        given(mongoTemplate.findById("c1", Conversation.class)).willReturn(conversation);
        MessageMetadata metadata = new MessageMetadata();
        metadata.setTurnId("turn-1");
        metadata.setThreadTurn(4);
        metadata.setSpecialistsUsed(List.of("faq_reader"));
        Message answer = message("c1", "assistant", "resposta 1", 2, T0.plusSeconds(5));
        answer.setMetadata(metadata);
        givenMessages(List.of(
                message("c1", "user", "pergunta 1", 1, T0),
                answer,
                message("c1", "user", "pergunta 2", 3, T0.plusSeconds(60)),
                message("c1", "assistant", "resposta 2", 4, T0.plusSeconds(70))));
        givenSteps(List.of(
                step("c1", "router", 1, "ok", 100.0, 0.001, T0.plusSeconds(2)),
                step("c1", "faq_reader", 2, "ok", 200.0, 0.002, T0.plusSeconds(4)),
                step("c1", "router", 1, "ok", 50.0, 0.0005, T0.plusSeconds(62))));
        given(mongoTemplate.find(any(Query.class), eq(Nps.class))).willReturn(List.of());

        AdminConversationDetailDTO detail = getConversation.execute("c1");

        assertThat(detail.threadId()).isEqualTo("debug:u:abc");
        assertThat(detail.messages()).hasSize(4);
        var first = detail.messages().get(1);
        assertThat(first.turn()).isEqualTo(1);
        assertThat(first.turnId()).isEqualTo("turn-1");
        assertThat(first.threadTurn()).isEqualTo(4);
        assertThat(first.specialistsUsed()).containsExactly("faq_reader");
        assertThat(first.steps()).extracting("node").containsExactly("router", "faq_reader");
        assertThat(first.latencyMs()).isEqualTo(300.0);
        var second = detail.messages().get(3);
        assertThat(second.turn()).isEqualTo(2);
        assertThat(second.steps()).hasSize(1);
        assertThat(second.costUsd()).isCloseTo(0.0005, within(1e-9));
        assertThat(detail.messages().get(0).steps()).isEmpty();
    }

    @Test
    void detalheDeConversaInexistenteOuQueNaoEhDoChatbotDa404() {
        given(mongoTemplate.findById("x", Conversation.class)).willReturn(null);
        Conversation direct = conversation("d", Map.of());
        direct.setConversationType(ConversationType.DIRECT);
        given(mongoTemplate.findById("d", Conversation.class)).willReturn(direct);

        assertThatThrownBy(() -> getConversation.execute("x")).isInstanceOf(ResourceNotFoundException.class);
        assertThatThrownBy(() -> getConversation.execute("d")).isInstanceOf(ResourceNotFoundException.class);
    }

    private void givenConversation(String id, Map<String, Object> details) {
        given(mongoTemplate.find(any(Query.class), eq(Conversation.class)))
                .willReturn(List.of(conversation(id, details)));
    }

    private void givenMessages(List<Message> messages) {
        given(mongoTemplate.find(any(Query.class), eq(Message.class))).willReturn(messages);
    }

    private void givenSteps(List<LlmObservability> steps) {
        given(mongoTemplate.find(any(Query.class), eq(LlmObservability.class))).willReturn(steps);
    }

    private static Conversation conversation(String id, Map<String, Object> details) {
        Conversation conversation = new Conversation();
        conversation.setId(id);
        conversation.setConversationType(ConversationType.CHAT_BOT);
        conversation.setCreatedBy(USER);
        conversation.setStartedAt(T0);
        conversation.setUserDetails(details);
        return conversation;
    }

    private static Message message(String conversationId, String role, String content, int sequence, Instant at) {
        Message message = new Message();
        message.setConversationId(conversationId);
        message.setRole(role);
        message.setContent(content);
        message.setSequence(sequence);
        message.setTimestamp(at);
        return message;
    }

    private static LlmObservability step(String conversationId, String node, int order, String status,
            double latency, double cost, Instant at) {
        LlmObservability step = new LlmObservability();
        step.setConversationId(conversationId);
        step.setNode(node);
        step.setStepOrder(order);
        step.setStepType(ObservabilityStepType.LLM_CALL);
        step.setModel("modelo");
        step.setStatus(status);
        step.setLatencyMs(latency);
        step.setCostUsd(cost);
        step.setTimestamp(at);
        return step;
    }

    private static Nps nps(String conversationId, int score) {
        Nps nps = new Nps();
        nps.setConversationId(conversationId);
        nps.setScore(score);
        return nps;
    }
}
