package com.solaria.messenger.core.contract.response;

import java.util.List;

public record LangSmithSummaryDTO(
        boolean enabled,
        String project,
        String projectUrl,
        LangSmithStatsDTO totals,
        List<LangSmithStatsDTO> byAssistant,
        List<LangSmithStatsDTO> byModel) {
}
