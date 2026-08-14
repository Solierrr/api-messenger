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
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.solaria.messenger.model.Message;
import com.solaria.messenger.service.MessageService;

@WebMvcTest(MessageController.class)
class MessageControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private MessageService messageService;

    @Test
    void sendsMessage() throws Exception {
        given(messageService.sendMessage(any(Message.class))).willReturn(message());

        mockMvc.perform(post("/api/v1/messages")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"conversationId\":\"conversation-1\",\"role\":\"user\",\"content\":\"Hello\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value("message-1"))
                .andExpect(jsonPath("$.content").value("Hello"));
    }

    @Test
    void getsMessagesByConversationId() throws Exception {
        given(messageService.getMessagesByConversationId(any(String.class), any(Pageable.class)))
                .willReturn(new PageImpl<>(List.of(message())));

        mockMvc.perform(get("/api/v1/messages/conversation/conversation-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content[0].conversationId").value("conversation-1"));
    }

    private Message message() {
        return new Message("message-1", "conversation-1", "user", "Hello", Instant.now());
    }
}
