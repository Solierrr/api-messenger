package com.solaria.messenger.model;

import java.time.Instant;
import java.util.UUID;

import org.springframework.data.mongodb.core.mapping.Field;

import com.solaria.messenger.model.enums.CommunityRole;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Membro embutido no documento {@link ProjectCommunity}
 * Cada membro pertence a uma {@link CommunityRole} que define suas permissões em {@link ProjectCommunity}
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class CommunityMember {

    @Field("user_id")
    private UUID userId;

    private CommunityRole role;

    @Field("joined_at")
    private Instant joinedAt;

    public static CommunityMember of(UUID userId, CommunityRole role, Instant joinedAt) {
        return new CommunityMember(userId, role, joinedAt);
    }
}
