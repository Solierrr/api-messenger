package com.solaria.messenger.security.rbac;

import java.util.Arrays;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.stereotype.Component;

import com.solaria.messenger.exception.UnauthorizedAccessException;

/**
 * Classe responsável por resolver RBAC de usuário -> feito a partir de ownership(dono da entidade)
 * Usando a claim authId do JWT vindo de api-auth
 */
@Component("rbac")
public class RbacAuthorizationService {


    // métodos chamados pelas classes Services

    public void requireOwnResource(UUID resourceOwnerId) {
        boolean isOwner = currentUserIdOptional()
                .map(currentUserId -> currentUserId.equals(resourceOwnerId))
                .orElse(false);
        if (!isOwner) {
            throw new UnauthorizedAccessException("O objeto da operação não foi encontrado.");
        }
    }
    public void requireParticipant(UUID... participantIds) {
        UUID currentUserId = currentUserId();
        boolean isParticipant = Arrays.stream(participantIds)
                .filter(Objects::nonNull)
                .anyMatch(currentUserId::equals);
        if (!isParticipant) {
            throw new UnauthorizedAccessException("O objeto da operação não foi encontrado.");
        }
    }

    // métodos de apoio da classe

    public UUID currentUserId() {
        return currentUserIdOptional()
            .orElseThrow(() -> new UnauthorizedAccessException("Nenhum usuário autenticado na requisição."));
    }

    private Optional<UUID> currentUserIdOptional() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (!(auth instanceof JwtAuthenticationToken jwtAuth)) {
            return Optional.empty();
        }
        return Optional.of(UUID.fromString(Objects.requireNonNull(jwtAuth.getToken().getSubject())));
    }
}
