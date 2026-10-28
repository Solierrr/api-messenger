package com.solaria.messenger.core.helper;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.solaria.messenger.core.exception.ResourceNotFoundException;
import com.solaria.messenger.core.exception.SuccessorIneligibleException;
import com.solaria.messenger.core.exception.UnauthorizedAccessException;
import com.solaria.messenger.core.integration.user.UserAccessClient;
import com.solaria.messenger.core.integration.user.dto.UserActivityBatchResponseDTO;
import com.solaria.messenger.core.integration.user.dto.UserActivityResponseDTO;

@Component
public class UserAccessHelper {

    private final UserAccessClient userAccessClient;

    public UserAccessHelper(UserAccessClient userAccessClient) {
        this.userAccessClient = userAccessClient;
    }

    /** Usado no CONNECT/reconnect e pelo filtro HTTP; falha técnica continua impedindo a operação. */
    public void requireActiveActor(UUID authId) {
        UserActivityResponseDTO activity;
        try {
            activity = userAccessClient.getActivity(authId);
        } catch (ResourceNotFoundException ex) {
            throw new UnauthorizedAccessException("O usuário autenticado não está cadastrado na Auth.");
        }
        if (!Boolean.TRUE.equals(activity.active())) {
            throw new UnauthorizedAccessException("O usuário autenticado está inativo na Auth.");
        }
    }

    /** Comandos administrativos mantêm validação integral dos convidados. */
    public void requireActiveUsers(Collection<UUID> authIds) {
        Objects.requireNonNull(authIds, "authIds");
        List<UUID> distinctAuthIds = List.copyOf(new LinkedHashSet<>(authIds));
        if (distinctAuthIds.isEmpty()) return;
        UserActivityBatchResponseDTO activity = userAccessClient.getActivityBatch(distinctAuthIds);
        if (!activity.missingAuthIds().isEmpty()) {
            throw new ResourceNotFoundException("Um ou mais usuários não foram encontrados na Auth.");
        }
        if (activity.users().stream().anyMatch(user -> !Boolean.TRUE.equals(user.active()))) {
            throw new UnauthorizedAccessException("Um ou mais usuários estão inativos na Auth.");
        }
    }

    /** Só respostas válidas permitem seleção parcial; falhas técnicas da Auth continuam abortando o comando. */
    public Set<UUID> eligibleUsers(Collection<UUID> authIds) {
        Set<UUID> requested = new LinkedHashSet<>(authIds);
        if (requested.isEmpty()) return Set.of();
        UserActivityBatchResponseDTO result = userAccessClient.getActivityBatch(requested);
        Set<UUID> eligible = new LinkedHashSet<>();
        result.users().stream().filter(user -> Boolean.TRUE.equals(user.active()))
                .forEach(user -> eligible.add(user.authId()));
        return Set.copyOf(eligible);
    }

    /** Sucessor inexistente ou inativo na Auth vira sucessor inelegível */
    public void requireSuccessorsActive(Collection<UUID> successors) {
        try {
            requireActiveUsers(successors);
        } catch (ResourceNotFoundException | UnauthorizedAccessException unavailable) {
            throw new SuccessorIneligibleException();
        }
    }
}
