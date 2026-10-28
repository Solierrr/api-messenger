package com.solaria.messenger.core.usecase.admin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.solaria.messenger.core.contract.response.LangSmithStatsDTO;
import com.solaria.messenger.core.contract.response.LangSmithSummaryDTO;
import com.solaria.messenger.core.integration.langsmith.LangSmithClient;

@ExtendWith(MockitoExtension.class)
class GetLangSmithSummaryUseCaseTests {

    private static final LangSmithStatsDTO TOTALS = new LangSmithStatsDTO("total", 4, 0, 10, 6, 4, 0.2, 100, 300);

    @Mock
    private LangSmithClient client;

    @Test
    void answersDisabledWithoutCallingLangSmithWhenNotConfigured() {
        given(client.enabled()).willReturn(false);

        LangSmithSummaryDTO summary = new GetLangSmithSummaryUseCase(client, "").execute(null, null);

        assertThat(summary.enabled()).isFalse();
        assertThat(summary.totals()).isNull();
        verify(client, never()).totals(any(), any());
    }

    @Test
    void coversTheWholeLastDayOfThePeriodInUtc() {
        given(client.enabled()).willReturn(true);
        given(client.project()).willReturn("solaria-qa");
        given(client.totals(any(), any())).willReturn(TOTALS);
        given(client.byAssistant(any(), any())).willReturn(List.of());
        given(client.byModel(any(), any())).willReturn(List.of());

        LangSmithSummaryDTO summary = new GetLangSmithSummaryUseCase(client, " https://smith/p ")
                .execute(LocalDate.parse("2026-10-20"), LocalDate.parse("2026-10-26"));

        ArgumentCaptor<Instant> start = ArgumentCaptor.forClass(Instant.class);
        ArgumentCaptor<Instant> end = ArgumentCaptor.forClass(Instant.class);
        verify(client).totals(start.capture(), end.capture());
        assertThat(start.getValue()).isEqualTo(Instant.parse("2026-10-20T00:00:00Z"));
        assertThat(end.getValue()).isEqualTo(Instant.parse("2026-10-27T00:00:00Z"));
        assertThat(summary.enabled()).isTrue();
        assertThat(summary.project()).isEqualTo("solaria-qa");
        assertThat(summary.projectUrl()).isEqualTo("https://smith/p");
        assertThat(summary.totals()).isEqualTo(TOTALS);
    }

    @Test
    void reusesTheAnswerForTheSamePeriodWhileItIsFresh() {
        given(client.enabled()).willReturn(true);
        given(client.totals(any(), any())).willReturn(TOTALS);
        given(client.byAssistant(any(), any())).willReturn(List.of());
        given(client.byModel(any(), any())).willReturn(List.of());
        GetLangSmithSummaryUseCase service = new GetLangSmithSummaryUseCase(client, "");
        LocalDate day = LocalDate.parse("2026-10-20");

        LangSmithSummaryDTO first = service.execute(day, day);
        LangSmithSummaryDTO second = service.execute(day, day);
        service.execute(day, day.plusDays(1));

        assertThat(second).isSameAs(first);
        verify(client, times(2)).totals(any(), any());
    }
}
