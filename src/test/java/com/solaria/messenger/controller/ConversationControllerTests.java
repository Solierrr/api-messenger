package com.solaria.messenger.controller;

import static org.mockito.ArgumentMatchers.any;
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

import com.solaria.messenger.dto.request.ChatbotConversationRequestDTO;
import com.solaria.messenger.dto.request.UserConversationRequestDTO;
import com.solaria.messenger.dto.response.ConversationResponseDTO;
import com.solaria.messenger.exception.handler.ProblemDetailFactory;
import com.solaria.messenger.model.enums.ConversationStatus;
import com.solaria.messenger.model.enums.ConversationType;
import com.solaria.messenger.model.enums.Environment;
import com.solaria.messenger.service.ConversationService;

@WebMvcTest(ConversationController.class)
@Import(ProblemDetailFactory.class)
class ConversationControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ConversationService conversationService;

    @Test
    void createsUserConversation() throws Exception {
        given(conversationService.createUserConversation(any(UserConversationRequestDTO.class)))
                .willReturn(conversationResponse());

        mockMvc.perform(post("/messaging/conversations/user-conversations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"receiverId\":\"" + UUID.randomUUID() + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("conversation-1"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    @Test
    void createsChatbotConversation() throws Exception {
        given(conversationService.createChatbotConversation(any(ChatbotConversationRequestDTO.class)))
                .willReturn(chatbotConversationResponse());

        mockMvc.perform(post("/messaging/conversations/chatbot-conversations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"environment\":\"LOCAL\",\"userType\":\"guest\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("conversation-1"))
                .andExpect(jsonPath("$.environment").value("LOCAL"));
    }

    @Test
    void rejectsInvalidEnvironment() throws Exception {
        mockMvc.perform(post("/messaging/conversations/chatbot-conversations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"environment\":\"INVALID\",\"userType\":\"guest\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getsConversationById() throws Exception {
        given(conversationService.findById("conversation-1"))
                .willReturn(conversationResponse());

        mockMvc.perform(get("/messaging/conversations/conversation-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("conversation-1"));
    }

    @Test
    void getsConversationsForCurrentUser() throws Exception {
        given(conversationService.findMine())
                .willReturn(List.of(conversationResponse()));

        mockMvc.perform(get("/messaging/conversations/me"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("conversation-1"));
    }

    private ConversationResponseDTO conversationResponse() {
        return ConversationResponseDTO.builder()
                .id("conversation-1")
                .senderId(UUID.randomUUID())
                .receiverId(UUID.randomUUID())
                .conversationType(ConversationType.USER_CONVERSATION)
                .status(ConversationStatus.ACTIVE)
                .startedAt(Instant.now())
                .lastInteractionAt(Instant.now())
                .build();
    }

    private ConversationResponseDTO chatbotConversationResponse() {
        return ConversationResponseDTO.builder()
                .id("conversation-1")
                .receiverId(UUID.randomUUID())
                .conversationType(ConversationType.CHAT_BOT)
                .environment(Environment.LOCAL)
                .userType("guest")
                .status(ConversationStatus.ACTIVE)
                .startedAt(Instant.now())
                .lastInteractionAt(Instant.now())
                .build();
    }
}