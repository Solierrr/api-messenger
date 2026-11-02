package com.solaria.messenger.core.integration.langsmith;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.mock.http.client.MockClientHttpRequest;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.test.web.client.RequestMatcher;
import org.springframework.web.client.RestClient;

import com.solaria.messenger.core.contract.response.LangSmithStatsDTO;
import com.solaria.messenger.core.exception.DependencyUnavailableException;

class LangSmithClientTests {

    private static final String PROJECT_ID = "11111111-1111-1111-1111-111111111111";
    private static final String SESSIONS = "http://ls/api/v1/sessions?name=solaria-qa&limit=20";
    private static final String STATS = "http://ls/api/v1/runs/stats";
    private static final Instant START = Instant.parse("2026-10-20T00:00:00Z");
    private static final Instant END = Instant.parse("2026-10-27T00:00:00Z");

    private MockRestServiceServer server;
    private LangSmithClient client;

    @BeforeEach
    void setUp() {
        RestClient.Builder builder = RestClient.builder().baseUrl("http://ls");
        server = MockRestServiceServer.bindTo(builder).build();
        client = new LangSmithClient(builder.build(), "solaria-qa", true);
    }

    private void expectProject() {
        server.expect(requestTo(SESSIONS)).andExpect(method(HttpMethod.GET))
                .andRespond(withSuccess("[{\"id\":\"" + PROJECT_ID + "\",\"name\":\"solaria-qa\"}]",
                        MediaType.APPLICATION_JSON));
    }

    private RequestMatcher bodyContains(String... parts) {
        return request -> {
            String body = ((MockClientHttpRequest) request).getBodyAsString();
            for (String part : parts) {
                assertThat(body).contains(part);
            }
        };
    }

    @Test
    void totalsReadRootRunsAndConvertLatencyToMilliseconds() {
        expectProject();
        server.expect(requestTo(STATS)).andExpect(method(HttpMethod.POST))
                .andExpect(bodyContains("\"session\":[\"" + PROJECT_ID + "\"]", "\"is_root\":true"))
                .andRespond(withSuccess("""
                        {"run_count":12,"error_rate":0.25,"total_tokens":3000,"prompt_tokens":2000,
                         "completion_tokens":1000,"total_cost":0.42,"latency_p50":1.5,"latency_p99":4.0}
                        """, MediaType.APPLICATION_JSON));

        LangSmithStatsDTO totals = client.totals(START, END);

        assertThat(totals.runs()).isEqualTo(12);
        assertThat(totals.errorRate()).isEqualTo(0.25);
        assertThat(totals.totalTokens()).isEqualTo(3000);
        assertThat(totals.costUsd()).isEqualTo(0.42);
        assertThat(totals.p50Ms()).isEqualTo(1500);
        assertThat(totals.p99Ms()).isEqualTo(4000);
        server.verify();
    }

    @Test
    void assistantTotalsAsksOnceAndFiltersTheRunsByTheAssistantMetadata() {
        expectProject();
        server.expect(requestTo(STATS)).andExpect(method(HttpMethod.POST))
                .andExpect(bodyContains("\"is_root\":true", "metadata_key", "\\\"assistant\\\"", "metadata_value",
                        "\\\"ai-assistant\\\""))
                .andRespond(withSuccess("{\"run_count\":5,\"error_rate\":0.2,\"total_cost\":0.01}",
                        MediaType.APPLICATION_JSON));

        LangSmithStatsDTO result = client.assistantTotals(START, END, "ai-assistant");

        assertThat(result.name()).isEqualTo("ai-assistant");
        assertThat(result.runs()).isEqualTo(5);
        server.verify();
    }

    @Test
    void assistantTotalsRejectsNamesThatCouldBreakTheFilter() {
        assertThatThrownBy(() -> client.assistantTotals(START, END, "a\"b")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> client.assistantTotals(START, END, "a\\b")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> client.assistantTotals(START, END, " ")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> client.assistantTotals(START, END, null)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void byAssistantGroupsByMetadataSortsByRunsAndDropsEmptyGroups() {
        expectProject();
        server.expect(requestTo(STATS))
                .andExpect(bodyContains("\"attribute\":\"metadata\"", "\"path\":\"assistant\"", "\"is_root\":true"))
                .andRespond(withSuccess("""
                        {"ai-operational":{"run_count":3,"total_cost":0.1},
                         "ai-assistant":{"run_count":9,"total_cost":0.5},
                         "ai-geographic":{"run_count":0}}
                        """, MediaType.APPLICATION_JSON));

        List<LangSmithStatsDTO> result = client.byAssistant(START, END);

        assertThat(result).extracting(LangSmithStatsDTO::name).containsExactly("ai-assistant", "ai-operational");
        assertThat(result.get(0).costUsd()).isEqualTo(0.5);
    }

    @Test
    void byModelLooksAtLlmRunsInsteadOfRootRuns() {
        expectProject();
        server.expect(requestTo(STATS))
                .andExpect(bodyContains("\"path\":\"ls_model_name\"", "\"run_type\":\"llm\""))
                .andRespond(withSuccess("{\"gpt-4o\":{\"run_count\":5,\"total_tokens\":100}}",
                        MediaType.APPLICATION_JSON));

        assertThat(client.byModel(START, END)).singleElement()
                .satisfies(model -> assertThat(model.name()).isEqualTo("gpt-4o"));
    }

    @Test
    void reusesTheProjectIdBetweenCalls() {
        expectProject();
        server.expect(requestTo(STATS)).andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));
        server.expect(requestTo(STATS)).andRespond(withSuccess("{}", MediaType.APPLICATION_JSON));

        client.totals(START, END);
        client.totals(START, END);

        server.verify();
    }

    @Test
    void failsWhenTheProjectDoesNotExist() {
        server.expect(requestTo(SESSIONS))
                .andRespond(withSuccess("[{\"id\":\"" + PROJECT_ID + "\",\"name\":\"outro\"}]",
                        MediaType.APPLICATION_JSON));

        assertThatThrownBy(() -> client.totals(START, END)).isInstanceOf(DependencyUnavailableException.class);
    }

    @Test
    void failsWhenLangSmithAnswersWithAnError() {
        expectProject();
        server.expect(requestTo(STATS)).andRespond(withServerError());

        assertThatThrownBy(() -> client.totals(START, END)).isInstanceOf(DependencyUnavailableException.class);
    }

    @Test
    void reportsRetryAfterAndBodyWhenLangSmithLimitsTheRequests() {
        expectProject();
        server.expect(requestTo(STATS)).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS)
                .header("Retry-After", "3600")
                .body("{\"detail\":\"Monthly\nusage limit reached\"}"));

        assertThatThrownBy(() -> client.totals(START, END))
                .isInstanceOf(DependencyUnavailableException.class)
                .hasMessageContaining("LangSmith respondeu 429 ao consultar as estatísticas")
                .hasMessageContaining("Retry-After: 3600s")
                .hasMessageContaining("Detalhe: {\"detail\":\"Monthly usage limit reached\"}");
    }

    @Test
    void keepsTheShortMessageWhenTheErrorHasNoHeaderOrBody() {
        expectProject();
        server.expect(requestTo(STATS)).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS));

        assertThatThrownBy(() -> client.totals(START, END))
                .isInstanceOf(DependencyUnavailableException.class)
                .hasMessage("LangSmith respondeu 429 ao consultar as estatísticas.");
    }

    @Test
    void cutsLongErrorBodiesAndAlsoReportsLimitsWhenLookingUpTheProject() {
        server.expect(requestTo(SESSIONS)).andRespond(withStatus(HttpStatus.TOO_MANY_REQUESTS).body("x".repeat(500)));

        assertThatThrownBy(() -> client.totals(START, END))
                .isInstanceOf(DependencyUnavailableException.class)
                .hasMessageContaining("LangSmith respondeu 429 ao buscar o projeto solaria-qa")
                .hasMessageContaining("x".repeat(200) + "...")
                .satisfies(error -> assertThat(error.getMessage()).doesNotContain("x".repeat(201)));
    }

    @Test
    void isDisabledWithoutKeyOrProject() {
        assertThat(client.enabled()).isTrue();
        assertThat(new LangSmithClient(RestClient.create(), "solaria-qa", false).enabled()).isFalse();
        assertThat(new LangSmithClient(RestClient.create(), "", true).enabled()).isFalse();
    }
}
