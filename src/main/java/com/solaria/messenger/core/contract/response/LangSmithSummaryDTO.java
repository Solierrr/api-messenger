package com.solaria.messenger.core.contract.response;

import java.util.List;

/** Resumo do LangSmith para o painel administrativo; {@code enabled} falso sem configuração */
public record LangSmithSummaryDTO(
        boolean enabled,
        String project,
        String projectUrl,
        LangSmithStatsDTO totals,
        List<LangSmithStatsDTO> byAssistant,
        List<LangSmithStatsDTO> byModel) {
}
