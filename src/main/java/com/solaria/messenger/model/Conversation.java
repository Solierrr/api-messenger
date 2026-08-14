package com.solaria.messenger.model;

import java.time.Instant;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "conversations")
public class Conversation {

    @Id
    private String id;

    @Indexed
    @Field("user_id")
    private Long userId;

    @Indexed(unique = true)
    @Field("session_id")
    private String sessionId;

    private String status;

    @Field("started_at")
    private Instant startedAt;

    @Field("last_interaction_at")
    private Instant lastInteractionAt;

    private Summary summary;
}
