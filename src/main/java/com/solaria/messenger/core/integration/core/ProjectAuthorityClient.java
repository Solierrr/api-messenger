package com.solaria.messenger.core.integration.core;

import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import com.solaria.messenger.core.exception.DependencyUnavailableException;
import com.solaria.messenger.core.exception.ResourceNotFoundException;
import com.solaria.messenger.core.exception.UnauthorizedAccessException;

/**
 * consulta a api-core para saber se um usuário é demandante do
 * projeto técnico, antes de deixá-lo criar a comunidade desse projeto
 *
 */
@Component
public class ProjectAuthorityClient {

    private final RestClient persistenceRestClient;

    public ProjectAuthorityClient(RestClient persistenceRestClient) {
        this.persistenceRestClient = persistenceRestClient;
    }

    /**
     * @throws ResourceNotFoundException projeto ou usuário (authId) inexistente na api-core
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
                        "Projeto ou usuário não encontrado na api-core: " + projectId);
                default -> throw new DependencyUnavailableException(
                        "api-core respondeu " + ex.getStatusCode().value()
                                + " ao verificar o demandante do projeto " + projectId, ex);
            }
        } catch (ResourceAccessException ex) {
            // timeout de conexão/leitura ou erro de rede /  api-core não respondeu a tempo
            throw new DependencyUnavailableException(
                    "api-core indisponível ao verificar o demandante do projeto " + projectId, ex);
        }
    }
}
