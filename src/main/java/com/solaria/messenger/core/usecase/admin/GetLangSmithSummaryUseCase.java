package com.solaria.messenger.core.usecase.admin;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.solaria.messenger.core.contract.response.LangSmithSummaryDTO;
import com.solaria.messenger.core.integration.langsmith.LangSmithClient;

/**
 * Caso de uso que monta o resumo do LangSmith para o painel administrativo.
 *
 * <p>
 * Junta os totais e os agrupamentos por assistente e por modelo do período. Sem datas, usa os últimos 7 dias até
 * hoje, em UTC, e o último dia entra inteiro. Sem o cliente configurado, responde desligado sem chamar o
 * LangSmith. A resposta de cada período fica em cache local por 2 minutos.
 * </p>
 *
 * <p>
 * <b>Depende de:</b>
 * </p>
 * <ul>
 *     <li>{@link LangSmithClient} -> consulta as estatísticas no LangSmith.</li>
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
    private final LangSmithClient client;
    private final String projectUrl;
    private final Map<String, Cached> cache = new ConcurrentHashMap<>();

    public GetLangSmithSummaryUseCase(LangSmithClient client,
            @Value("${app.langsmith.project-url:}") String projectUrl) {
        this.client = client;
        this.projectUrl = projectUrl == null || projectUrl.isBlank() ? null : projectUrl.trim();
    }

    /**
     * Devolve o resumo do LangSmith do período.
     *
     * @param from primeiro dia do período. Sem valor, sete dias antes do fim.
     * @param to último dia do período, incluído. Sem valor, hoje em UTC.
     * @return resumo com projeto, totais e agrupamentos, ou um resumo desligado quando não há configuração.
     */
    public LangSmithSummaryDTO execute(LocalDate from, LocalDate to) {
        if (!client.enabled()) {
            return new LangSmithSummaryDTO(false, null, null, null, List.of(), List.of());
        }
        LocalDate end = to != null ? to : LocalDate.now(ZoneOffset.UTC);
        LocalDate start = from != null ? from : end.minusDays(DEFAULT_DAYS);

        // o cache é deste pod e evita consultar o LangSmith a cada abertura do painel
        String key = start + "|" + end;
        Cached cached = cache.get(key);
        if (cached != null && cached.expiresAt().isAfter(Instant.now())) {
            return cached.value();
        }

        Instant startInstant = start.atStartOfDay(ZoneOffset.UTC).toInstant();
        Instant endInstant = end.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant();
        LangSmithSummaryDTO value = new LangSmithSummaryDTO(
                true,
                client.project(),
                projectUrl,
                client.totals(startInstant, endInstant),
                client.byAssistant(startInstant, endInstant),
                client.byModel(startInstant, endInstant));
        cache.put(key, new Cached(value, Instant.now().plus(TTL)));
        return value;
    }

    private record Cached(LangSmithSummaryDTO value, Instant expiresAt) {
    }
}
