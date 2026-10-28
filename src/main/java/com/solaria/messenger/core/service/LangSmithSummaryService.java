package com.solaria.messenger.core.service;

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

@Service
public class LangSmithSummaryService {

    static final Duration TTL = Duration.ofMinutes(2);
    private static final int DEFAULT_DAYS = 7;

    private final LangSmithClient client;
    private final String projectUrl;
    private final Map<String, Cached> cache = new ConcurrentHashMap<>();

    public LangSmithSummaryService(LangSmithClient client, @Value("${app.langsmith.project-url:}") String projectUrl) {
        this.client = client;
        this.projectUrl = projectUrl == null || projectUrl.isBlank() ? null : projectUrl.trim();
    }

    public LangSmithSummaryDTO summary(LocalDate from, LocalDate to) {
        if (!client.enabled()) {
            return new LangSmithSummaryDTO(false, null, null, null, List.of(), List.of());
        }
        LocalDate end = to != null ? to : LocalDate.now(ZoneOffset.UTC);
        LocalDate start = from != null ? from : end.minusDays(DEFAULT_DAYS);
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
