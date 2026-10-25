package com.solaria.messenger.core.model;

import java.time.Instant;
import java.util.UUID;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import com.solaria.messenger.core.model.enums.Environment;
import com.solaria.messenger.core.model.enums.NpsClassification;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Resposta de NPS de uma conversa com o chatbot
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "nps_responses")
public class Nps {

    @Id
    private String id;

    @Indexed(unique = true)
    @Field("conversation_id")
    private String conversationId;

    @Indexed
    @Field("user_id")
    private UUID userId;

    private int score;

    private NpsClassification classification;

    private Environment environment;

    private Instant timestamp;
}
