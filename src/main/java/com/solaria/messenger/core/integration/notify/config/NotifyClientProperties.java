package com.solaria.messenger.core.integration.notify.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Propriedades do cliente HTTP para o api-notify; sem {@code baseUrl} o envio de push fica desligado
 */
@ConfigurationProperties(prefix = "app.integration.notify")
public record NotifyClientProperties(String baseUrl,
        @DefaultValue("PT2S") Duration connectTimeout,
        @DefaultValue("PT3S") Duration readTimeout,
        String serviceKey) {
}
