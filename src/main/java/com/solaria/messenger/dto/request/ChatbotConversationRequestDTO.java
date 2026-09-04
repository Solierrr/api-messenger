package com.solaria.messenger.dto.request;

import java.util.Map;

import com.solaria.messenger.model.enums.Environment;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;


@Getter
@Setter
public class ChatbotConversationRequestDTO {

    @NotNull(message = "environment é obrigatório")
    private Environment environment;

    @NotBlank(message = "userType é obrigatório")
    private String userType;

    private Map<String, Object> userDetails;
}
