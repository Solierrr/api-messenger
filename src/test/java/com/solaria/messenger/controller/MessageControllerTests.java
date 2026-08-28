package com.solaria.messenger.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.solaria.messenger.dto.request.MessageRequestDTO;
import com.solaria.messenger.dto.response.MessageResponseDTO;
import com.solaria.messenger.exception.handler.ProblemDetailFactory;
import com.solaria.messenger.model.enums.MessageType;
import com.solaria.messenger.model.enums.Environment;
import com.solaria.messenger.service.MessageService;

@WebMvcTest(MessageController.class)
@Import(ProblemDetailFactory.class)
class MessageControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MessageService messageService;

    @Test
    void sendsMessage() throws Exception {
        given(messageService.sendUserMessage(any(MessageRequestDTO.class))).willReturn(messageResponse());

        mockMvc.perform(post("/messaging/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"conversationId\":\"conversation-1\",\"messageType\":\"USER_TO_USER\","
                                + "\"role\":\"user\",\"content\":\"Hello\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("message-1"))
                .andExpect(jsonPath("$.content").value("Hello"));
    }

    @Test
    void sendsMessageToChatbotWithEnvironment() throws Exception {
        given(messageService.sendUserMessage(any(MessageRequestDTO.class)))
                .willReturn(messageResponseWithEnvironment());

        mockMvc.perform(post("/messaging/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"conversationId\":\"conversation-1\","
                                + "\"messageType\":\"USER_TO_CHATBOT\","
                                + "\"role\":\"user\","
                                + "\"environment\":\"QA\","
                                + "\"content\":\"Como escolher uma placa solar?\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("message-1"))
                .andExpect(jsonPath("$.environment").value("QA"))
                .andExpect(jsonPath("$.messageType").value("USER_TO_CHATBOT"));
    }

    @Test
    void getsMessagesByConversationId() throws Exception {
        given(messageService.getMessagesByConversationId(eq("conversation-1")))
                .willReturn(List.of(messageResponse()));

        mockMvc.perform(get("/messaging/messages/conversation/conversation-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].conversationId").value("conversation-1"));
    }

    private MessageResponseDTO messageResponse() {
        return MessageResponseDTO.builder()
                .id("message-1")
                .conversationId("conversation-1")
                .senderId(UUID.randomUUID())
                .role("user")
                .messageType(MessageType.USER_TO_USER)
                .content("Hello")
                .timestamp(Instant.now())
                .build();
    }

    private MessageResponseDTO messageResponseWithEnvironment() {
        return MessageResponseDTO.builder()
                .id("message-1")
                .conversationId("conversation-1")
                .senderId(UUID.randomUUID())
                .role("user")
                .messageType(MessageType.USER_TO_CHATBOT)
                .environment(Environment.QA)
                .content("Como escolher uma placa solar?")
                .timestamp(Instant.now())
                .build();
    }
}
