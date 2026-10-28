package com.solaria.messenger.core.contract.response;

/** Estatísticas do LangSmith de um grupo de runs; latências em milissegundos */
public record LangSmithStatsDTO(
        String name,
        long runs,
        double errorRate,
        long totalTokens,
        long promptTokens,
        long completionTokens,
        double costUsd,
        double p50Ms,
        double p99Ms) {
}
