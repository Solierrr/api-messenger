package com.solaria.messenger.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.solaria.messenger.model.Conversation;
import com.solaria.messenger.service.ConversationService;

@WebMvcTest(ConversationController.class)
class ConversationControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ConversationService conversationService;

    @Test
    void createsConversation() throws Exception {
        Conversation conversation = conversation();
        given(conversationService.createConversation(any(Conversation.class))).willReturn(conversation);

        mockMvc.perform(post("/api/v1/conversations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\":1,\"sessionId\":\"session-1\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("conversation-1"))
                .andExpect(jsonPath("$.status").value("active"));
    }

    @Test
    void getsConversationById() throws Exception {
        given(conversationService.getConversationById("conversation-1")).willReturn(conversation());

        mockMvc.perform(get("/api/v1/conversations/conversation-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.sessionId").value("session-1"));
    }

    @Test
    void getsConversationsByUserId() throws Exception {
        given(conversationService.getConversationsByUserId(1L)).willReturn(List.of(conversation()));

        mockMvc.perform(get("/api/v1/conversations/user/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].userId").value(1));
    }

    private Conversation conversation() {
        return new Conversation("conversation-1", 1L, "session-1", "active", Instant.now(), Instant.now(), null);
    }
}
