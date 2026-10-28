package com.solaria.messenger.rest.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.solaria.messenger.core.contract.request.MessageRequestDTO;
import com.solaria.messenger.core.contract.response.MessageResponseDTO;
import com.solaria.messenger.rest.error.ProblemDetailFactory;
import com.solaria.messenger.core.model.enums.MessageType;
import com.solaria.messenger.core.security.rbac.RbacAuthorizationService;
import com.solaria.messenger.core.usecase.message.DeleteMessageUseCase;
import com.solaria.messenger.core.usecase.message.EditMessageUseCase;
import com.solaria.messenger.core.usecase.message.ListConversationMessagesUseCase;
import com.solaria.messenger.core.usecase.message.SendUserMessageUseCase;

@WebMvcTest(MessageController.class)
@Import(ProblemDetailFactory.class)
class MessageControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SendUserMessageUseCase sendMessage;

    @MockitoBean
    private EditMessageUseCase editMessage;

    @MockitoBean
    private DeleteMessageUseCase deleteMessage;

    @MockitoBean
    private ListConversationMessagesUseCase listMessages;

    // dependências do StompErrorHandler, que é @ControllerAdvice e entra no slice do @WebMvcTest
    @MockitoBean
    private RbacAuthorizationService rbac;

    @MockitoBean
    private SimpMessagingTemplate messaging;

    @Test
    void sendsGroupMessage() throws Exception {
        given(sendMessage.execute(any(MessageRequestDTO.class)))
                .willReturn(messageResponse(MessageType.USER_TO_GROUP));

        mockMvc.perform(post("/messaging/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"conversationId\":\"conv-1\",\"messageType\":\"USER_TO_GROUP\","
                                + "\"role\":\"user\",\"content\":\"Bom dia, time\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("msg-1"))
                .andExpect(jsonPath("$.messageType").value("USER_TO_GROUP"))
                .andExpect(jsonPath("$.environment").doesNotExist());
    }

    @Test
    void sendMessageResponseHasNoLocationHeader() throws Exception {
        // F-17: sem GET por id de mensagem, o header antigo apontava para uma rota inexistente
        // (e resolveria via Kong para o serviço errado) - a resposta não deve ter Location.
        given(sendMessage.execute(any(MessageRequestDTO.class)))
                .willReturn(messageResponse(MessageType.USER_TO_GROUP));

        mockMvc.perform(post("/messaging/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"conversationId\":\"conv-1\",\"messageType\":\"USER_TO_GROUP\","
                                + "\"role\":\"user\",\"content\":\"Bom dia, time\"}"))
                .andExpect(status().isCreated())
                .andExpect(header().doesNotExist("Location"));
    }

    @Test
    void rejectsMessageWithoutContent() throws Exception {
        mockMvc.perform(post("/messaging/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"conversationId\":\"conv-1\",\"messageType\":\"USER_TO_USER\",\"role\":\"user\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void acceptsMessageWithoutRole() throws Exception {
        // F-21: role é derivado no servidor - o campo deixou de ser @NotBlank no DTO.
        given(sendMessage.execute(any(MessageRequestDTO.class)))
                .willReturn(messageResponse(MessageType.USER_TO_USER));

        mockMvc.perform(post("/messaging/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"conversationId\":\"conv-1\",\"messageType\":\"USER_TO_USER\","
                                + "\"content\":\"Bom dia\"}"))
                .andExpect(status().isCreated());
    }

    @Test
    void getsMessagesByConversationId() throws Exception {
        given(listMessages.execute(eq("conv-1"), isNull(), isNull(), isNull()))
                .willReturn(List.of(messageResponse(MessageType.USER_TO_GROUP)));

        mockMvc.perform(get("/messaging/messages/conversation/conv-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].conversationId").value("conv-1"))
                .andExpect(jsonPath("$[0].environment").doesNotExist());
    }

    @Test
    void passesSinceSequenceAndLimitToService() throws Exception {
        given(listMessages.execute(eq("conv-1"), eq(5), isNull(), eq(20)))
                .willReturn(List.of(messageResponse(MessageType.USER_TO_GROUP)));

        mockMvc.perform(get("/messaging/messages/conversation/conv-1")
                        .param("sinceSequence", "5")
                        .param("limit", "20"))
                .andExpect(status().isOk());

        verify(listMessages).execute("conv-1", 5, null, 20);
    }

    private MessageResponseDTO messageResponse(MessageType type) {
        return MessageResponseDTO.builder()
                .id("msg-1")
                .conversationId("conv-1")
                .senderId(UUID.randomUUID())
                .role("user")
                .messageType(type)
                .content("Bom dia, time")
                .timestamp(Instant.now())
                .build();
    }
}
