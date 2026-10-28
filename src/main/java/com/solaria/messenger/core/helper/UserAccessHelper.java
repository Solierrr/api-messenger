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

/**
 * Confere na Auth se os users estão cadastrados e ativos.
 *
 * <p>
 * A Auth é a fonte da situação do user. Falha técnica ao consultar a Auth continua bloqueando a operação. Só
 * respostas válidas permitem seleção parcial.
 * </p>
 *
 * <p>
 * <b>Depende de:</b>
 * </p>
 * <ul>
 *     <li>{@link UserAccessClient} -> consulta a situação dos users na Auth.</li>
 * </ul>
 *
 * <p>
 * Principais operações disponibilizadas:
 * </p>
 * <ul>
 *     <li>{@link #requireActiveActor(UUID)} -> exige que o ator esteja ativo.</li>
 *     <li>{@link #requireActiveUsers(Collection)} -> exige que todos os usuários informados estejam ativos.</li>
 *     <li>{@link #eligibleUsers(Collection)} -> devolve só os usuários ativos.</li>
 *     <li>{@link #requireSuccessorsActive(Collection)} -> exige que os sucessores estejam ativos.</li>
 * </ul>
 */
@Component
public class UserAccessHelper {

    private final UserAccessClient userAccessClient;

    public UserAccessHelper(UserAccessClient userAccessClient) {
        this.userAccessClient = userAccessClient;
    }

    /**
     * Confere que o ator está cadastrado e ativo na Auth.
     *
     * <p>
     * Usado no CONNECT, no reconnect e pelo filtro HTTP. Falha técnica ao consultar a Auth não é tratada aqui, e a
     * operação para.
     * </p>
     *
     * @param authId identificador do ator na Auth.
     * @throws UnauthorizedAccessException caso o ator não esteja cadastrado ou esteja inativo.
     */
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

    /**
     * Confere que todos os usuários informados existem e estão ativos na Auth.
     *
     * <p>
     * Usado nos comandos administrativos, que validam todos os convidados. Ids repetidos contam uma vez só.
     * </p>
     *
     * @param authIds identificadores na Auth. Lista vazia não faz consulta.
     * @throws ResourceNotFoundException caso algum usuário não exista na Auth.
     * @throws UnauthorizedAccessException caso algum usuário esteja inativo.
     */
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

    /**
     * Devolve os usuários ativos entre os informados.
     *
     * <p>
     * Usuários inativos ou inexistentes ficam de fora, sem gerar erro. Falha técnica na Auth continua abortando o
     * comando.
     * </p>
     *
     * @param authIds identificadores na Auth.
     * @return conjunto com os usuários ativos.
     */
    public Set<UUID> eligibleUsers(Collection<UUID> authIds) {
        Set<UUID> requested = new LinkedHashSet<>(authIds);
        if (requested.isEmpty()) return Set.of();
        UserActivityBatchResponseDTO result = userAccessClient.getActivityBatch(requested);
        Set<UUID> eligible = new LinkedHashSet<>();
        result.users().stream().filter(user -> Boolean.TRUE.equals(user.active()))
                .forEach(user -> eligible.add(user.authId()));
        return Set.copyOf(eligible);
    }

    /**
     * Confere que os sucessores estão ativos, convertendo a falha em erro de sucessor.
     *
     * <p>
     * Sucessor inexistente ou inativo na Auth vira {@link SuccessorIneligibleException}.
     * </p>
     *
     * @param successors identificadores dos sucessores.
     * @throws SuccessorIneligibleException caso algum sucessor não esteja ativo ou não exista na Auth.
     */
    public void requireSuccessorsActive(Collection<UUID> successors) {
        try {
            requireActiveUsers(successors);
        } catch (ResourceNotFoundException | UnauthorizedAccessException unavailable) {
            throw new SuccessorIneligibleException();
        }
    }
}
