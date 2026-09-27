package com.solaria.messenger.dto.request;

import java.util.Map;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;


@Getter
@Setter
public class ChatbotConversationRequestDTO {

    @NotBlank(message = "userType é obrigatório")
    private String userType;

    private Map<String, Object> userDetails;
}
