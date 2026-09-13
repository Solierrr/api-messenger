package com.solaria.messenger.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import com.solaria.messenger.model.enums.CommunityRole;
import com.solaria.messenger.model.enums.CommunityStatus;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * comunidade de um projeto | tipo slack para troca de mensagens entre membros de um projeto
 * Referencia um projeto de {@code api-persistence} pelo campo {@link #projectId}
 * 
 * <p>
 * Os membros ficam embutidos ({@link CommunityMember})
 * {@link CommunityRole} define as permissões disponíveis por user dentro da comunidade 
 * </p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Document(collection = "project_communities")
@CompoundIndex(name = "project_community_member_idx", def = "{'members.user_id': 1}")
public class ProjectCommunity {

    @Id
    private String id;

    /** Projeto técnico do api-core
     *  uma comunidade por projeto */
    @Indexed(unique = true)
    @Field("project_id")
    private UUID projectId;

    private List<CommunityMember> members = new ArrayList<>();

    private CommunityStatus status;

    @Field("created_by")
    private UUID createdBy;

    @Field("created_at")
    private Instant createdAt;

    @Field("updated_at")
    private Instant updatedAt;

    public Optional<CommunityMember> member(UUID userId) {
        return members == null ? Optional.empty()
                : members
                .stream()
                .filter(m ->
                 m.getUserId().equals(userId)).findFirst();
    }

    public boolean isMember(UUID userId) {
        return member(userId).isPresent();
    }

    public boolean hasRole(UUID userId, CommunityRole role) {
        return member(userId)
        .map(m ->
         m.getRole() == role).orElse(false);
    }

    /** OWNER ou ADMIN. */
    public boolean isAdmin(UUID userId) {
        return member(userId)
                .map(m ->
                 m.getRole() == CommunityRole.OWNER || m.getRole() == CommunityRole.ADMIN)
                .orElse(false);
    }

    public boolean isOwner(UUID userId) {
        return hasRole(userId, CommunityRole.OWNER);
    }

    public Set<UUID> memberIds() {
        return members == null ? Set.of()
                : members.stream()
                .map(CommunityMember::getUserId).collect(Collectors.toSet());
    }
}
