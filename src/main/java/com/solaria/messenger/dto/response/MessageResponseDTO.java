package com.solaria.messenger.dto.response;

import java.time.Instant;
import java.util.UUID;

import com.solaria.messenger.model.MessageMetadata;
import com.solaria.messenger.model.enums.MessageType;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MessageResponseDTO {

    private String id;
    private String conversationId;
    private UUID senderId;
    private String role;
    private MessageType messageType;
    private String content;
    private MessageMetadata metadata;
    private Instant timestamp;
}
