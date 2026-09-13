package com.solaria.messenger.model;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;
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


/**
 * dados de uma conversa, a participação é baseada por {@link #participantIds}
 *
 * <ul>
 *   <li>{@code DIRECT} -> {@code participantIds} tem exatamente 2 UUIDs</li>
 *   <li>{@code GROUP} -> {@code participantIds} tem 2+ UUIDs 
 *       {@code projectId} -> preenchido quando o grupo pertence a um projeto</li>
 *   <li>{@code CHAT_BOT} -> {@code participantIds} tem só o UUID do usuário dono da conversa</li>
 * </ul>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "conversations")
public class Conversation {

    @Id
    private String id;

    @Field("conversation_type")
    private ConversationType conversationType;

    @Indexed
    @Field("participant_ids")
    private Set<UUID> participantIds = new LinkedHashSet<>();

    @Field("created_by")
    private UUID createdBy;

    /**  prenchido apenas para {@code GROUP} */
    private String title;

    @Indexed
    @Field("community_id")
    private String communityId;

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

    public boolean hasParticipant(UUID userId) {
        return participantIds != null && participantIds.contains(userId);
    }
}
