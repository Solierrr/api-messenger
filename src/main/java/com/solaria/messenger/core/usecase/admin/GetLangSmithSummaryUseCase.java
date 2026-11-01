package com.solaria.messenger.core.usecase.admin;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.MongoTemplate;
import org.springframework.stereotype.Service;

import com.solaria.messenger.core.contract.response.LangSmithSummaryDTO;
import com.solaria.messenger.core.integration.langsmith.LangSmithClient;

/**
 * Caso de uso que monta o resumo do LangSmith para o painel administrativo.
 *
 * <p>
 * Traz só os totais do assistente {@code ai-assistant} no período, em uma única consulta ao LangSmith; os
 * agrupamentos por assistente e por modelo voltam vazios. Sem datas, usa os últimos 7 dias até hoje, em UTC, e o
 * último dia entra inteiro. Sem o cliente configurado, responde desligado sem chamar o LangSmith. A resposta de
 * cada período fica em cache no MongoDB por 2 minutos, o mesmo para todos os pods.
 * </p>
 *
 * <p>
 * <b>Depende de:</b>
 * </p>
 * <ul>
 *     <li>{@link LangSmithClient} -> consulta as estatísticas no LangSmith.</li>
 *     <li>{@link MongoTemplate} -> guarda o cache do resumo em {@code langsmith_summary_cache}.</li>
 * </ul>
 *
 * <p>
 * Principal operação disponibilizada:
 * </p>
 * <ul>
 *     <li>{@link #execute(LocalDate, LocalDate)} -> devolve o resumo do período.</li>
 * </ul>
 */
@Service
public class GetLangSmithSummaryUseCase {

    private static final Duration TTL = Duration.ofMinutes(2);
    private static final int DEFAULT_DAYS = 7;
    private static final String CACHE = "langsmith_summary_cache";
    private static final String ASSISTANT = "ai-assistant";
    private final LangSmithClient client;
    private final MongoTemplate mongoTemplate;
    private final String projectUrl;

    public GetLangSmithSummaryUseCase(LangSmithClient client, MongoTemplate mongoTemplate,
            @Value("${app.langsmith.project-url:}") String projectUrl) {
        this.client = client;
        this.mongoTemplate = mongoTemplate;
        this.projectUrl = projectUrl == null || projectUrl.isBlank() ? null : projectUrl.trim();
    }

    /**
     * Devolve o resumo do LangSmith do período.
     *
     * @param from primeiro dia do período. Sem valor, sete dias antes do fim.
     * @param to último dia do período, incluído. Sem valor, hoje em UTC.
     * @return resumo com projeto e totais do {@code ai-assistant}, ou um resumo desligado quando não há configuração.
     */
    public LangSmithSummaryDTO execute(LocalDate from, LocalDate to) {
        if (!client.enabled()) {
            return new LangSmithSummaryDTO(false, null, null, null, List.of(), List.of());
        }
        LocalDate end = to != null ? to : LocalDate.now(ZoneOffset.UTC);
        LocalDate start = from != null ? from : end.minusDays(DEFAULT_DAYS);

        // o cache é compartilhado entre os pods e evita consultar o LangSmith a cada abertura do painel
        String key = ASSISTANT + "|" + start + "|" + end;
        CachedSummary cached = mongoTemplate.findById(key, CachedSummary.class, CACHE);
        if (cached != null && cached.expiresAt().isAfter(Instant.now())) {
            return cached.value();
        }

        Instant startInstant = start.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant endInstant = end.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        // uma consulta só (a de totais do ai-assistant): menos chamadas à API de estatísticas do LangSmith, que recusa com 429
        LangSmithSummaryDTO value = new LangSmithSummaryDTO(
                true,
                client.project(),
                projectUrl,
                client.assistantTotals(startInstant, endInstant, ASSISTANT),
                List.of(),
                List.of());
        mongoTemplate.save(new CachedSummary(key, value, Instant.now().plus(TTL)), CACHE);
        return value;
    }

    /** Resumo guardado de um período, válido até {@code expiresAt}. */
    public record CachedSummary(@Id String id, LangSmithSummaryDTO value, Instant expiresAt) {
    }
}
