package com.solaria.messenger.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.solaria.messenger.dto.request.LlmObservabilityRequestDTO;
import com.solaria.messenger.dto.response.LlmObservabilityResponseDTO;
import com.solaria.messenger.model.LlmObservability;
import com.solaria.messenger.model.enums.ObservabilityStepType;
import com.solaria.messenger.repository.LlmObservabilityRepository;

@ExtendWith(MockitoExtension.class)
class LlmObservabilityServiceTests {

    @Mock
    private LlmObservabilityRepository llmObservabilityRepository;

    @InjectMocks
    private LlmObservabilityService llmObservabilityService;

    @Test
    void defaultsCostUsdToZeroWhenNotInformed() {
        // Compatibilidade de rollout: versões antigas do ai-assistant ainda
        // não mandam costUsd — não pode virar null no banco nem quebrar o ingest.
        LlmObservabilityRequestDTO dto = requestDto();
        dto.setCostUsd(null);

        given(llmObservabilityRepository.save(any(LlmObservability.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        LlmObservabilityResponseDTO response = llmObservabilityService.ingest(dto);

        assertThat(response.getCostUsd()).isEqualTo(0.0);
    }

    @Test
    void persistsInformedCostUsd() {
        LlmObservabilityRequestDTO dto = requestDto();
        dto.setCostUsd(0.0042);

        given(llmObservabilityRepository.save(any(LlmObservability.class)))
                .willAnswer(invocation -> invocation.getArgument(0));

        LlmObservabilityResponseDTO response = llmObservabilityService.ingest(dto);

        assertThat(response.getCostUsd()).isEqualTo(0.0042);
    }

    private LlmObservabilityRequestDTO requestDto() {
        LlmObservabilityRequestDTO dto = new LlmObservabilityRequestDTO();
        dto.setNode("router");
        dto.setStepOrder(1);
        dto.setStepType(ObservabilityStepType.LLM_CALL);
        dto.setModel("gemini-2.5-flash");
        dto.setConversationId("conversation-1");
        dto.setTokensIn(10);
        dto.setTokensOut(5);
        dto.setTokensTotal(15);
        dto.setLatencyMs(120.5);
        dto.setStatus("ok");

        return dto;
    }
}
