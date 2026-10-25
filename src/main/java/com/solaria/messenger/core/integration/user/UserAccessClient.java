package com.solaria.messenger.core.integration.user;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import com.solaria.messenger.core.exception.DependencyUnavailableException;
import com.solaria.messenger.core.exception.ResourceNotFoundException;
import com.solaria.messenger.core.integration.user.dto.UserActivityBatchResponseDTO;
import com.solaria.messenger.core.integration.user.dto.UserActivityResponseDTO;

/**
 * Cliente que consulta na api-auth a atividade dos usuários (conta ativa ou inativa).
 *
 * <p>Principais operações disponibilizadas:</p>
 * <ul>
 *     <li>{@link #getActivity(UUID)} -> consulta a atividade de um usuário.</li>
 *     <li>{@link #getActivityBatch(Collection)} -> consulta a atividade de vários usuários em uma chamada.</li>
 * </ul>
 */
@Component
public class UserAccessClient {

    private final RestClient authRestClient;

    public UserAccessClient(@Qualifier("authRestClient") RestClient authRestClient) {
        this.authRestClient = authRestClient;
    }

    /** Consulta a atividade de um usuário; 404 vira recurso inexistente e as demais falhas, dependência indisponível. */
    public UserActivityResponseDTO getActivity(UUID authId) {
        try {
            UserActivityResponseDTO response = authRestClient.get()
                    .uri("/internal/users/{authId}/activity", authId)
                    .retrieve()
                    .body(UserActivityResponseDTO.class);
            if (response == null || !authId.equals(response.authId()) || response.active() == null) {
                throw unavailable("Auth retornou atividade inválida para " + authId);
            }
            return response;
        } catch (RestClientResponseException ex) {
            if (ex.getStatusCode().value() == 404) {
                throw new ResourceNotFoundException("Usuário não encontrado na Auth: " + authId);
            }
            throw unavailable("Auth respondeu " + ex.getStatusCode().value() + " ao consultar atividade", ex);
        } catch (RestClientException ex) {
            throw unavailable("Falha ao consultar atividade do usuário na Auth: " + authId, ex);
        }
    }

    /** Consulta a atividade de vários usuários numa única chamada e valida a cobertura da resposta. */
    public UserActivityBatchResponseDTO getActivityBatch(Collection<UUID> authIds) {
        Objects.requireNonNull(authIds, "authIds");
        List<UUID> requested = new ArrayList<>(new LinkedHashSet<>(authIds));
        if (requested.isEmpty()) {
            return new UserActivityBatchResponseDTO(List.of(), List.of());
        }

        try {
            UserActivityBatchResponseDTO response = authRestClient.post()
                    .uri("/internal/users/activity/batch")
                    .body(Map.of("authIds", requested))
                    .retrieve()
                    .body(UserActivityBatchResponseDTO.class);
            validateBatchResponse(requested, response);
            return response;
        } catch (RestClientException ex) {
            throw unavailable("Falha técnica na consulta batch de atividade na Auth", ex);
        }
    }

    /** Exige que cada ID pedido apareça exatamente uma vez, como ativo/inativo ou ausente. */
    private void validateBatchResponse(List<UUID> requested, UserActivityBatchResponseDTO response) {
        if (response == null || response.users() == null || response.missingAuthIds() == null) {
            throw unavailable("Auth retornou corpo batch incompleto");
        }

        Set<UUID> requestedSet = new HashSet<>(requested);
        Set<UUID> found = new HashSet<>();
        for (UserActivityResponseDTO user : response.users()) {
            if (user == null || user.authId() == null || user.active() == null
                    || !requestedSet.contains(user.authId()) || !found.add(user.authId())) {
                throw unavailable("Auth retornou atividade batch inválida");
            }
        }

        Set<UUID> missing = new HashSet<>();
        for (UUID authId : response.missingAuthIds()) {
            if (authId == null || !requestedSet.contains(authId) || found.contains(authId) || !missing.add(authId)) {
                throw unavailable("Auth retornou ausências batch inválidas");
            }
        }
        if (found.size() + missing.size() != requestedSet.size()) {
            throw unavailable("Auth retornou cobertura batch incompleta");
        }
    }

    /** Falha técnica da Auth, sem causa associada. */
    private DependencyUnavailableException unavailable(String message) {
        return new DependencyUnavailableException(message);
    }

    /** Falha técnica da Auth, preservando a causa original. */
    private DependencyUnavailableException unavailable(String message, Throwable cause) {
        return new DependencyUnavailableException(message, cause);
    }
}
