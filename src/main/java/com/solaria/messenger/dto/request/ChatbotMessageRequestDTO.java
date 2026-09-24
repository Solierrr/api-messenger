package com.solaria.messenger.dto.request;

import com.solaria.messenger.model.MessageMetadata;
import com.solaria.messenger.model.enums.Environment;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;


@Getter
@Setter
public class ChatbotMessageRequestDTO {

    @NotBlank(message = "conversationId é obrigatório")
    private String conversationId;

    @NotNull(message = "environment é obrigatório")
    private Environment environment;

    @NotBlank(message = "content é obrigatório")
    private String content;

    private MessageMetadata metadata;
}
