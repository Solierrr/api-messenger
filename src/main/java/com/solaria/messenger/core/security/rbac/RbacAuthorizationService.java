package com.solaria.messenger.core.security.rbac;

import java.util.Arrays;
import java.util.Collection;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import com.solaria.messenger.core.exception.UnauthorizedAccessException;

/**
 * Classe responsável por resolver RBAC de usuário -> feito a partir de ownership(dono da entidade)
 * Usando a claim authId do JWT vindo de api-auth
 */
@Component("rbac")
public class RbacAuthorizationService {


    // métodos chamados pelas classes Services

    /** Exige que o usuário autenticado seja o dono do recurso; a recusa não revela se o recurso existe. */
    public void requireOwnResource(UUID resourceOwnerId) {
        boolean isOwner = currentUserIdOptional()
                .map(currentUserId -> currentUserId.equals(resourceOwnerId))
                .orElse(false);
        if (!isOwner) {
            throw new UnauthorizedAccessException("O objeto da operação não foi encontrado.");
        }
    }
    /** Exige que o usuário autenticado esteja entre os IDs informados. */
    public void requireParticipant(UUID... participantIds) {
        UUID currentUserId = currentUserId();
        boolean isParticipant = Arrays.stream(participantIds)
                .filter(Objects::nonNull)
                .anyMatch(currentUserId::equals);
        if (!isParticipant) {
            throw new UnauthorizedAccessException("O objeto da operação não foi encontrado.");
        }
    }

    /** Exige que o usuário autenticado esteja na coleção de participantes. */
    public void requireParticipant(Collection<UUID> participantIds) {
        UUID currentUserId = currentUserId();
        boolean isParticipant = participantIds != null && participantIds.stream()
                .filter(Objects::nonNull)
                .anyMatch(currentUserId::equals);
        if (!isParticipant) {
            throw new UnauthorizedAccessException("O objeto da operação não foi encontrado.");
        }
    }

    // métodos de apoio da classe

    /** ID do usuário autenticado (subject do JWT); sem autenticação lança 403. */
    public UUID currentUserId() {
        return currentUserIdOptional()
            .orElseThrow(() -> new UnauthorizedAccessException("Nenhum usuário autenticado na requisição."));
    }

    /** Indica se o ID informado é o do usuário autenticado. */
    public boolean isCurrentUser(UUID userId) {
        return currentUserIdOptional().map(id -> id.equals(userId)).orElse(false);
    }

    /** Subject do JWT do contexto de segurança atual, quando houver. */
    private Optional<UUID> currentUserIdOptional() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (!(auth instanceof JwtAuthenticationToken jwtAuth)) {
            return Optional.empty();
        }
        return Optional.of(UUID.fromString(Objects.requireNonNull(jwtAuth.getToken().getSubject())));
    }
}
