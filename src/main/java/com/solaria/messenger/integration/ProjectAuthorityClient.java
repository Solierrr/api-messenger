package com.solaria.messenger.integration;

import com.solaria.messenger.exception.*;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import org.springframework.web.client.*;

import java.util.*;

@Component
public class ProjectAuthorityClient {
    private final RestClient client;
    private final String key;

    public ProjectAuthorityClient(
            RestClient persistenceRestClient,
            @Value("${app.integration.persistence.project-key:}") String key) {
        this.client = persistenceRestClient;
        this.key = key;
    }

    public record Access(String role, boolean readOnly, Map<UUID, String> members, String name) {}

    public Access access(UUID projectId, UUID authId) {
        if (key.length() < 32)
            throw new DependencyUnavailableException(
                    "Project authority is not configured",
                    new IllegalStateException("Missing service key"));
        try {
            var result =
                    client.get()
                            .uri("/internal/projects/{id}/access/{authId}", projectId, authId)
                            .header("X-Project-Service-Key", key)
                            .retrieve()
                            .body(Access.class);
            if (result == null
                    || result.role() == null
                    || !Set.of("OWNER", "MANAGER", "MEMBER", "VIEWER").contains(result.role())
                    || result.members() == null
                    || !result.role().equals(result.members().get(authId)))
                throw new UnauthorizedAccessException("Você não participa do projeto");
            return result;
        } catch (RestClientResponseException e) {
            if (e.getStatusCode().value() == 403 || e.getStatusCode().value() == 404)
                throw new UnauthorizedAccessException("Projeto indisponível");
            throw new DependencyUnavailableException("Project authority unavailable", e);
        } catch (ResourceAccessException e) {
            throw new DependencyUnavailableException("Project authority unavailable", e);
        }
    }

    public void requireRequester(UUID projectId, UUID authId) {
        var a = access(projectId, authId);
        if (!"OWNER".equals(a.role()) && !"MANAGER".equals(a.role()))
            throw new UnauthorizedAccessException(
                    "Apenas responsáveis podem gerenciar a comunidade");
    }

    public Access write(UUID projectId, UUID authId) {
        var a = access(projectId, authId);
        if (a.readOnly() || "VIEWER".equals(a.role()))
            throw new UnauthorizedAccessException("Projeto disponível apenas para consulta");
        return a;
    }
}
