package com.solaria.messenger.rest.controller;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.solaria.messenger.core.contract.response.MessageResponseDTO;
import com.solaria.messenger.core.contract.response.MessageUpdatesResponseDTO;
import com.solaria.messenger.core.exception.InvalidFieldException;
import com.solaria.messenger.core.model.enums.MessageType;
import com.solaria.messenger.core.security.rbac.RbacAuthorizationService;
import com.solaria.messenger.core.usecase.message.ListMessageUpdatesUseCase;
import com.solaria.messenger.rest.error.ProblemDetailFactory;

@WebMvcTest(UpdatesController.class)
@Import(ProblemDetailFactory.class)
class UpdatesControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ListMessageUpdatesUseCase listUpdates;

    // dependências do StompErrorHandler, que é @ControllerAdvice e entra no slice do @WebMvcTest
    @MockitoBean
    private RbacAuthorizationService rbac;

    @MockitoBean
    private SimpMessagingTemplate messaging;

    @Test
    void withoutParametersAsksForTheStartingPoint() throws Exception {
        given(listUpdates.execute(isNull(), isNull()))
                .willReturn(new MessageUpdatesResponseDTO("epoch-1", 500L, false, List.of()));

        mockMvc.perform(get("/messaging/updates"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.syncEpoch").value("epoch-1"))
                .andExpect(jsonPath("$.cursor").value(500))
                .andExpect(jsonPath("$.hasMore").value(false))
                .andExpect(jsonPath("$.messages").isEmpty());
    }

    @Test
    void passesSinceAndLimitAndReturnsTheMessagesWithTheirChangeVersion() throws Exception {
        given(listUpdates.execute(eq(5L), eq(20)))
                .willReturn(new MessageUpdatesResponseDTO("epoch-1", 9L, true, List.of(message())));

        mockMvc.perform(get("/messaging/updates").param("since", "5").param("limit", "20"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cursor").value(9))
                .andExpect(jsonPath("$.hasMore").value(true))
                .andExpect(jsonPath("$.messages[0].id").value("msg-1"))
                .andExpect(jsonPath("$.messages[0].changeVersion").value(9));

        verify(listUpdates).execute(5L, 20);
    }

    @Test
    void negativeSinceIsRejectedAsInvalidField() throws Exception {
        given(listUpdates.execute(eq(-1L), isNull()))
                .willThrow(new InvalidFieldException("since deve ser maior ou igual a zero."));

        mockMvc.perform(get("/messaging/updates").param("since", "-1"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.errorCode").value("INVALID_FIELD"));
    }

    @Test
    void nonNumericSinceIsABadRequest() throws Exception {
        mockMvc.perform(get("/messaging/updates").param("since", "abc"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(listUpdates);
    }

    private MessageResponseDTO message() {
        return MessageResponseDTO.builder()
                .id("msg-1")
                .conversationId("conv-1")
                .senderId(UUID.randomUUID())
                .role("user")
                .messageType(MessageType.USER_TO_GROUP)
                .content("Bom dia, time")
                .timestamp(Instant.now())
                .changeVersion(9L)
                .build();
    }
}
