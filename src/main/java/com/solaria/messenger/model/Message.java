package com.solaria.messenger.model;

import java.time.Instant;
import java.util.UUID;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
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
@CompoundIndex(name = "conversation_client_message_idx",
        def = "{'conversation_id': 1, 'client_message_id': 1}",
        unique = true, sparse = true)
@CompoundIndex(name = "conversation_sequence_idx",
        def = "{'conversation_id': 1, 'sequence': 1}")
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

    /** preenchido quando a mensagem chega via websocker para idempotencia*/
    @Field("client_message_id")
    private String clientMessageId;

    @Field("sequence")
    private int sequence;

    @Field("broadcasted_at")
    private Instant broadcastedAt;

    @Field("broadcast_attempts")
    private int broadcastAttempts;
}
