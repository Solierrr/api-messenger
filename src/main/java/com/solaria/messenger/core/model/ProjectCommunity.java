package com.solaria.messenger.core.model;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.Version;
import org.springframework.data.mongodb.core.index.CompoundIndex;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.mongodb.core.mapping.Field;

import com.solaria.messenger.core.model.enums.CommunityRole;
import com.solaria.messenger.core.model.enums.CommunityStatus;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * comunidade de um projeto | tipo slack para troca de mensagens entre membros de um projeto
 * Referencia um projeto de {@code api-core} pelo campo {@link #projectId}
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

    @Version
    private Long version;

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

    /** Membro da comunidade com o ID informado, se existir. */
    public Optional<CommunityMember> member(UUID userId) {
        return members == null ? Optional.empty()
                : members
                .stream()
                .filter(m ->
                 m.getUserId().equals(userId)).findFirst();
    }

    /** Indica se o usuário é membro da comunidade. */
    public boolean isMember(UUID userId) {
        return member(userId).isPresent();
    }

    /** Indica se o usuário é membro com exatamente o papel informado. */
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

    /** Indica se o usuário é o dono da comunidade. */
    public boolean isOwner(UUID userId) {
        return hasRole(userId, CommunityRole.OWNER);
    }

    /** IDs de todos os membros da comunidade. */
    public Set<UUID> memberIds() {
        return members == null ? Set.of()
                : members.stream()
                .map(CommunityMember::getUserId).collect(Collectors.toSet());
    }

}
