package com.solaria.messenger.core.integration.core;

import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import com.solaria.messenger.core.exception.DependencyUnavailableException;
import com.solaria.messenger.core.exception.UnauthorizedAccessException;
import com.solaria.messenger.core.integration.core.config.CoreClientProperties;
import com.solaria.messenger.core.integration.core.dto.ProjectAccessResponseDTO;

/**
 * Consulta na api-core o acesso de um usuário a um projeto técnico, autenticando com {@code X-Service-Key}
 *
 * <p>Principais operações disponibilizadas:</p>
 * <ul>
 *     <li>{@link #access(UUID, UUID)} -> papel do usuário, envolvidos e situação do projeto.</li>
 *     <li>{@link #requireManager(UUID, UUID)} -> exige que o usuário seja responsável (OWNER ou MANAGER) pelo projeto.</li>
 *     <li>{@link #requireWritable(UUID, UUID)} -> o mesmo acesso, exigindo que o usuário possa escrever.</li>
 * </ul>
 */
@Component
public class ProjectAuthorityClient {

    static final String SERVICE_KEY_HEADER = "X-Service-Key";
    static final int MIN_SERVICE_KEY_LENGTH = 32;
    private static final Set<String> ROLES = Set.of("OWNER", "MANAGER", "MEMBER", "VIEWER");

    private final RestClient persistenceRestClient;
    private final String serviceKey;

    public ProjectAuthorityClient(RestClient persistenceRestClient, CoreClientProperties properties) {
        this.persistenceRestClient = persistenceRestClient;
        this.serviceKey = properties.serviceKey();
    }

    /**
     * @throws UnauthorizedAccessException usuário fora do projeto, projeto inexistente ou papel desconhecido
     * @throws DependencyUnavailableException chave de serviço ausente, timeout, erro de rede ou resposta 5xx/inesperada
     */
    public ProjectAccessResponseDTO access(UUID projectId, UUID authId) {
        if (serviceKey == null || serviceKey.length() < MIN_SERVICE_KEY_LENGTH) {
            throw new DependencyUnavailableException("A autoridade de projeto não está configurada.");
        }
        try {
            ProjectAccessResponseDTO response = persistenceRestClient.get()
                    .uri("/internal/projects/{projectId}/access/{authId}", projectId, authId)
                    .header(SERVICE_KEY_HEADER, serviceKey)
                    .retrieve()
                    .body(ProjectAccessResponseDTO.class);

            if (response == null || response.role() == null || !ROLES.contains(response.role())
                    || response.members() == null || !response.role().equals(response.members().get(authId))) {
                throw new UnauthorizedAccessException("Você não participa do projeto.");
            }
            return response;
        } catch (RestClientResponseException ex) {
            int status = ex.getStatusCode().value();
            if (status == 403 || status == 404) {
                throw new UnauthorizedAccessException("Projeto indisponível.");
            }
            throw new DependencyUnavailableException(
                    "api-core respondeu " + status + " ao consultar o acesso ao projeto " + projectId, ex);
        } catch (ResourceAccessException ex) {
            // timeout de conexão/leitura ou erro de rede / api-core não respondeu a tempo
            throw new DependencyUnavailableException(
                    "api-core indisponível ao consultar o acesso ao projeto " + projectId, ex);
        }
    }

    /** Exige que o usuário seja responsável pelo projeto (OWNER ou MANAGER) */
    public ProjectAccessResponseDTO requireManager(UUID projectId, UUID authId) {
        ProjectAccessResponseDTO access = access(projectId, authId);
        if (!"OWNER".equals(access.role()) && !"MANAGER".equals(access.role())) {
            throw new UnauthorizedAccessException("Apenas responsáveis pelo projeto podem criar a comunidade.");
        }
        return access;
    }

    /** Acesso ao projeto para escrita: projeto encerrado ou papel de leitor só consultam */
    public ProjectAccessResponseDTO requireWritable(UUID projectId, UUID authId) {
        ProjectAccessResponseDTO access = access(projectId, authId);
        if (access.readOnly() || "VIEWER".equals(access.role())) {
            throw new UnauthorizedAccessException("Projeto disponível apenas para consulta.");
        }
        return access;
    }
}
