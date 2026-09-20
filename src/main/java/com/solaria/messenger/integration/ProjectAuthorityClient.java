package com.solaria.messenger.integration;

import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import com.solaria.messenger.exception.DependencyUnavailableException;
import com.solaria.messenger.exception.ResourceNotFoundException;
import com.solaria.messenger.exception.UnauthorizedAccessException;

/**
 * consulta a api-persistence para saber se um usuário é demandante do
 * projeto técnico, antes de deixá-lo criar a comunidade desse projeto
 *
 * <p>
 * Chamada síncrona, sem retry
 * qualquer coisa além de 204/403/404 vira {@link DependencyUnavailableException} (503)
 * </p>
 */
@Component
public class ProjectAuthorityClient {

    private final RestClient persistenceRestClient;

    public ProjectAuthorityClient(RestClient persistenceRestClient) {
        this.persistenceRestClient = persistenceRestClient;
    }

    /**
     * @throws ResourceNotFoundException projeto ou usuário (authId) inexistente na api-persistence
     * @throws UnauthorizedAccessException usuário não pertence à empresa demandante do projeto
     * @throws DependencyUnavailableException timeout, erro de rede ou resposta 5xx/inesperada
     */
    public void requireRequester(UUID projectId, UUID authId) {
        try {
            persistenceRestClient.get()
                    .uri("/internal/technical-projects/{projectId}/requester-access/{authId}", projectId, authId)
                    .retrieve()
                    .toBodilessEntity();
        } catch (RestClientResponseException ex) {
            switch (ex.getStatusCode().value()) {
                case 403 -> throw new UnauthorizedAccessException(
                        "Apenas o demandante do projeto pode criar a comunidade.");
                case 404 -> throw new ResourceNotFoundException(
                        "Projeto ou usuário não encontrado na api-persistence: " + projectId);
                default -> throw new DependencyUnavailableException(
                        "api-persistence respondeu " + ex.getStatusCode().value()
                                + " ao verificar o demandante do projeto " + projectId, ex);
            }
        } catch (ResourceAccessException ex) {
            // timeout de conexão/leitura ou erro de rede /  api-persistence não respondeu a tempo
            throw new DependencyUnavailableException(
                    "api-persistence indisponível ao verificar o demandante do projeto " + projectId, ex);
        }
    }
}
