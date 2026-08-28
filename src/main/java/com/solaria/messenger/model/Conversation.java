package com.solaria.messenger.model;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import com.solaria.messenger.model.enums.ConversationStatus;
import com.solaria.messenger.model.enums.ConversationType;
import com.solaria.messenger.model.enums.Environment;

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
    @Field("sender_id")
    private UUID senderId;

    @Indexed
    @Field("receiver_id")
    private UUID receiverId;

    @Field("conversation_type")
    private ConversationType conversationType;

    @Field("environment")
    private Environment environment;

    @Field("user_type")
    private String userType;

    @Field("user_details")
    private Map<String, Object> userDetails;

    private ConversationStatus status;

    @Field("started_at")
    private Instant startedAt;

    @Field("last_interaction_at")
    private Instant lastInteractionAt;
}
