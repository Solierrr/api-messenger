package com.solaria.messenger.core.integration.langsmith;

import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import com.solaria.messenger.core.contract.response.LangSmithStatsDTO;
import com.solaria.messenger.core.exception.DependencyUnavailableException;

/**
 * Lê do LangSmith as estatísticas dos runs raiz do projeto (um run raiz = um turno do assistente).
 * A chave fica só aqui no backend; o web-sandbox nunca a recebe.
 */
@Component
public class LangSmithClient {

    private static final List<String> SELECT = List.of(
            "run_count", "error_rate", "total_tokens", "prompt_tokens", "completion_tokens",
            "total_cost", "latency_p50", "latency_p99");
    private static final int MAX_GROUPS = 20;
    private static final int MAX_REASON_CHARS = 200;

    private final RestClient restClient;
    private final String project;
    private final boolean hasKey;
    private volatile UUID projectId;

    @Autowired
    public LangSmithClient(
            @Value("${app.langsmith.url:https://api.smith.langchain.com}") String url,
            @Value("${app.langsmith.api-key:}") String apiKey,
            @Value("${app.langsmith.project:}") String project,
            @Value("${app.langsmith.timeout-seconds:10}") int timeoutSeconds) {
        this(build(url, apiKey, timeoutSeconds), project, apiKey != null && !apiKey.isBlank());
    }

    LangSmithClient(RestClient restClient, String project, boolean hasKey) {
        this.restClient = restClient;
        this.project = project == null ? "" : project.trim();
        this.hasKey = hasKey;
    }

    private static RestClient build(String url, String apiKey, int timeoutSeconds) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout((int) Duration.ofSeconds(timeoutSeconds).toMillis());
        factory.setReadTimeout((int) Duration.ofSeconds(timeoutSeconds).toMillis());
        RestClient.Builder builder = RestClient.builder().baseUrl(url).requestFactory(factory);
        if (apiKey != null && !apiKey.isBlank()) {
            builder.defaultHeader("X-API-Key", apiKey);
        }
        return builder.build();
    }

    public boolean enabled() {
        return hasKey && !project.isBlank();
    }

    public String project() {
        return project;
    }

    public LangSmithStatsDTO totals(Instant start, Instant end) {
        Map<String, Object> raw = stats(start, end, null, null);
        return toStats("total", raw);
    }

    public List<LangSmithStatsDTO> byAssistant(Instant start, Instant end) {
        return grouped(stats(start, end, "assistant", null));
    }

    public List<LangSmithStatsDTO> byModel(Instant start, Instant end) {
        return grouped(stats(start, end, "ls_model_name", "llm"));
    }

    private Map<String, Object> stats(Instant start, Instant end, String groupPath, String runType) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("session", List.of(projectId().toString()));
        body.put("start_time", start.toString());
        body.put("end_time", end.toString());
        body.put("select", SELECT);
        if (runType == null) {
            body.put("is_root", true);
        } else {
            body.put("run_type", runType);
        }
        if (groupPath != null) {
            Map<String, Object> groupBy = new LinkedHashMap<>();
            groupBy.put("attribute", "metadata");
            groupBy.put("path", groupPath);
            groupBy.put("max_groups", MAX_GROUPS);
            body.put("group_by", groupBy);
        }
        try {
            Map<String, Object> response = restClient.post()
                    .uri("/api/v1/runs/stats")
                    .body(body)
                    .retrieve()
                    .body(new ParameterizedTypeReference<Map<String, Object>>() { });
            return response == null ? Map.of() : response;
        } catch (RestClientResponseException ex) {
            throw new DependencyUnavailableException(
                    "LangSmith respondeu " + ex.getStatusCode().value() + " ao consultar as estatísticas" + reason(ex) + ".",
                    ex);
        } catch (ResourceAccessException ex) {
            throw new DependencyUnavailableException("LangSmith indisponível ao consultar as estatísticas.", ex);
        }
    }

    private static String reason(RestClientResponseException ex) {
        StringBuilder reason = new StringBuilder();
        String retryAfter = ex.getResponseHeaders() == null ? null : ex.getResponseHeaders().getFirst("Retry-After");
        if (retryAfter != null && !retryAfter.isBlank()) {
            reason.append(" (Retry-After: ").append(retryAfter.trim()).append("s)");
        }
        String body = ex.getResponseBodyAsString().replaceAll("\\s+", " ").trim();
        if (!body.isEmpty()) {
            String excerpt = body.length() > MAX_REASON_CHARS ? body.substring(0, MAX_REASON_CHARS) + "..." : body;
            reason.append(". Detalhe: ").append(excerpt);
        }
        return reason.toString();
    }

    private UUID projectId() {
        UUID cached = projectId;
        if (cached != null) {
            return cached;
        }
        try {
            List<Map<String, Object>> sessions = restClient.get()
                    .uri(uri -> uri.path("/api/v1/sessions").queryParam("name", project).queryParam("limit", 20)
                            .build())
                    .retrieve()
                    .body(new ParameterizedTypeReference<List<Map<String, Object>>>() { });
            if (sessions != null) {
                for (Map<String, Object> session : sessions) {
                    if (project.equals(session.get("name")) && session.get("id") != null) {
                        projectId = UUID.fromString(String.valueOf(session.get("id")));
                        return projectId;
                    }
                }
            }
        } catch (RestClientResponseException ex) {
            throw new DependencyUnavailableException(
                    "LangSmith respondeu " + ex.getStatusCode().value() + " ao buscar o projeto " + project + reason(ex) + ".",
                    ex);
        } catch (ResourceAccessException ex) {
            throw new DependencyUnavailableException("LangSmith indisponível ao buscar o projeto " + project + ".", ex);
        }
        throw new DependencyUnavailableException("Projeto " + project + " não encontrado no LangSmith.");
    }

    private static List<LangSmithStatsDTO> grouped(Map<String, Object> raw) {
        return raw.entrySet().stream()
                .filter(entry -> entry.getValue() instanceof Map<?, ?>)
                .map(entry -> toStats(entry.getKey(), asMap(entry.getValue())))
                .filter(stats -> stats.runs() > 0)
                .sorted((a, b) -> Long.compare(b.runs(), a.runs()))
                .toList();
    }

    private static Map<String, Object> asMap(Object value) {
        Map<String, Object> copy = new LinkedHashMap<>();
        ((Map<?, ?>) value).forEach((key, item) -> copy.put(String.valueOf(key), item));
        return copy;
    }

    static LangSmithStatsDTO toStats(String name, Map<String, Object> raw) {
        return new LangSmithStatsDTO(
                name,
                (long) number(raw.get("run_count")),
                number(raw.get("error_rate")),
                (long) number(raw.get("total_tokens")),
                (long) number(raw.get("prompt_tokens")),
                (long) number(raw.get("completion_tokens")),
                number(raw.get("total_cost")),
                number(raw.get("latency_p50")) * 1000,
                number(raw.get("latency_p99")) * 1000);
    }

    private static double number(Object value) {
        return value instanceof Number n ? n.doubleValue() : 0;
    }
}
