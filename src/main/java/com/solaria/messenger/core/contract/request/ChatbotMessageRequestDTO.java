package com.solaria.messenger.core.contract.request;

import com.solaria.messenger.core.model.MessageMetadata;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;


/**
 * DTO de requisição para ingestão de uma mensagem produzida pelo chatbot.
 */
@Getter
@Setter
public class ChatbotMessageRequestDTO {

    @NotBlank(message = "conversationId é obrigatório")
    private String conversationId;

    @NotBlank(message = "content é obrigatório")
    private String content;

    private MessageMetadata metadata;
}
