package com.solaria.messenger.core.integration.notify;

import java.util.Map;
import java.util.UUID;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import com.solaria.messenger.core.integration.notify.config.NotifyClientProperties;

/**
 * Cliente do api-notify para disparar push a um usuário
 *
 * <p>Principais operações disponibilizadas:</p>
 * <ul>
 *     <li>{@link #isEnabled()} -> indica se há URL do api-notify configurada.</li>
 *     <li>{@link #dispatch(UUID, String, String, String, Map)} -> pede o envio de uma notificação.</li>
 * </ul>
 */
@Component
public class NotifyClient {

    private final RestClient notifyRestClient;
    private final boolean enabled;

    public NotifyClient(RestClient notifyRestClient, NotifyClientProperties properties) {
        this.notifyRestClient = notifyRestClient;
        this.enabled = properties.baseUrl() != null && !properties.baseUrl().isBlank();
    }

    /** Sem URL configurada o push fica desligado. */
    public boolean isEnabled() {
        return enabled;
    }

    /** Pede ao api-notify que grave e envie a notificação; qualquer falha sobe como {@code RestClientException}. */
    public void dispatch(UUID authId, String type, String title, String body, Map<String, String> data) {
        notifyRestClient.post()
                .uri("/internal/notifications")
                .body(Map.of("authId", authId, "type", type, "title", title, "body", body, "data", data))
                .retrieve()
                .toBodilessEntity();
    }
}
