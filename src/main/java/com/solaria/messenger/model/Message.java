package com.solaria.messenger.model;

import java.time.Instant;
import java.util.UUID;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import com.solaria.messenger.model.enums.Environment;
import com.solaria.messenger.model.enums.MessageType;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;


@Data
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "messages")
public class Message {

    @Id
    private String id;

    @Indexed
    @Field("conversation_id")
    private String conversationId;

    @Field("sender_id")
    private UUID senderId;

    private String role;

    @Field("environment")
    private Environment environment;

    @Field("message_type")
    private MessageType messageType;

    private String content;

    private MessageMetadata metadata;

    private Instant timestamp;
}
