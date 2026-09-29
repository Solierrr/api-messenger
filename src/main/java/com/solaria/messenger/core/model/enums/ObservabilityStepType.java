package com.solaria.messenger.core.model.enums;

/**
 * Tipo de passo registrado na telemetria do pipeline de LLM: chamada a modelo ({@code LLM_CALL})
 * ou chamada a ferramenta ({@code TOOL_CALL}).
 */
public enum ObservabilityStepType {
    LLM_CALL,
    TOOL_CALL
}
