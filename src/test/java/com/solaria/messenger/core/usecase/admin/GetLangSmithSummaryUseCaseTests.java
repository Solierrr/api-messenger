package com.solaria.messenger.core.usecase.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;

import com.solaria.messenger.core.contract.response.LangSmithStatsDTO;
import com.solaria.messenger.core.contract.response.LangSmithSummaryDTO;
import com.solaria.messenger.core.integration.langsmith.LangSmithClient;

@ExtendWith(MockitoExtension.class)
class GetLangSmithSummaryUseCaseTests {

    private static final LangSmithStatsDTO TOTALS = new LangSmithStatsDTO("total", 4, 0, 10, 6, 4, 0.2, 100, 300);

    @Mock
    private LangSmithClient client;

    @Mock
    private MongoTemplate mongoTemplate;

    @Test
    void answersDisabledWithoutCallingLangSmithWhenNotConfigured() {
        given(client.enabled()).willReturn(false);

        LangSmithSummaryDTO summary = new GetLangSmithSummaryUseCase(client, mongoTemplate, "").execute(null, null);

        assertThat(summary.enabled()).isFalse();
        assertThat(summary.totals()).isNull();
        verify(client, never()).assistantTotals(any(), any(), any());
    }

    @Test
    void coversTheWholeLastDayOfThePeriodInUtc() {
        given(client.enabled()).willReturn(true);
        given(client.project()).willReturn("solaria-qa");
        given(client.assistantTotals(any(), any(), eq("ai-assistant"))).willReturn(TOTALS);

        LangSmithSummaryDTO summary = new GetLangSmithSummaryUseCase(client, mongoTemplate, " https://smith/p ")
                .execute(LocalDate.parse("2026-10-20"), LocalDate.parse("2026-10-26"));

        ArgumentCaptor<Instant> start = ArgumentCaptor.forClass(Instant.class);
        ArgumentCaptor<Instant> end = ArgumentCaptor.forClass(Instant.class);
        verify(client).assistantTotals(start.capture(), end.capture(), eq("ai-assistant"));
        assertThat(start.getValue()).isEqualTo(Instant.parse("2026-10-20T00:00:00Z"));
        assertThat(end.getValue()).isEqualTo(Instant.parse("2026-10-27T00:00:00Z"));
        assertThat(summary.enabled()).isTrue();
        assertThat(summary.project()).isEqualTo("solaria-qa");
        assertThat(summary.projectUrl()).isEqualTo("https://smith/p");
        assertThat(summary.totals()).isEqualTo(TOTALS);
    }

    @Test
    void asksLangSmithOnceAndLeavesTheGroupingsEmpty() {
        given(client.enabled()).willReturn(true);
        given(client.project()).willReturn("solaria-qa");
        given(client.assistantTotals(any(), any(), eq("ai-assistant"))).willReturn(TOTALS);

        LangSmithSummaryDTO summary = new GetLangSmithSummaryUseCase(client, mongoTemplate, "")
                .execute(LocalDate.parse("2026-10-20"), LocalDate.parse("2026-10-26"));

        assertThat(summary.byAssistant()).isEmpty();
        assertThat(summary.byModel()).isEmpty();
        verify(client, never()).totals(any(), any());
        verify(client, never()).byAssistant(any(), any());
        verify(client, never()).byModel(any(), any());
    }

    @Test
    void reusesTheSharedAnswerForTheSamePeriodWhileItIsFresh() {
        given(client.enabled()).willReturn(true);
        LangSmithSummaryDTO stored = new LangSmithSummaryDTO(true, "solaria-qa", null, TOTALS, List.of(), List.of());
        given(mongoTemplate.findById(eq("ai-assistant|2026-10-20|2026-10-20"), eq(GetLangSmithSummaryUseCase.CachedSummary.class),
                eq("langsmith_summary_cache")))
                .willReturn(new GetLangSmithSummaryUseCase.CachedSummary("ai-assistant|2026-10-20|2026-10-20", stored,
                        Instant.now().plusSeconds(60)));
        LocalDate day = LocalDate.parse("2026-10-20");

        LangSmithSummaryDTO summary = new GetLangSmithSummaryUseCase(client, mongoTemplate, "").execute(day, day);

        assertThat(summary).isEqualTo(stored);
        verify(client, never()).assistantTotals(any(), any(), any());
    }
}
