package com.solaria.messenger.core.contract.request;

import java.util.Map;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;


/**
 * DTO de requisição para criação de uma conversa com o chatbot.
 *
 */
@Getter
@Setter
public class ChatbotConversationRequestDTO {

    @NotBlank(message = "userType é obrigatório")
    private String userType;

    private Map<String, Object> userDetails;
}
