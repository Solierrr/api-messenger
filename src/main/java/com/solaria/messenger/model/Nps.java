package com.solaria.messenger.model;

import java.time.Instant;
import java.util.UUID;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import com.solaria.messenger.model.enums.Environment;
import com.solaria.messenger.model.enums.NpsClassification;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Resposta de NPS de uma conversa com o chatbot. O índice único em
 * {@code conversation_id} garante uma única resposta por conversa.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "nps")
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
