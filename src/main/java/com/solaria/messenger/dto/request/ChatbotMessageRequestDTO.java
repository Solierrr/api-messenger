package com.solaria.messenger.dto.request;

import com.solaria.messenger.model.MessageMetadata;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.Setter;


@Getter
@Setter
public class ChatbotMessageRequestDTO {

    @NotBlank(message = "conversationId é obrigatório")
    private String conversationId;

    @NotBlank(message = "content é obrigatório")
    private String content;

    private MessageMetadata metadata;
}
