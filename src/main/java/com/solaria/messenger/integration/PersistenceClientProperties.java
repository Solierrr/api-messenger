package com.solaria.messenger.integration;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Propriedades do cliente HTTP para a api-persistence 
 */
@ConfigurationProperties(prefix = "app.integration.persistence")
public record PersistenceClientProperties(String baseUrl, Duration connectTimeout, Duration readTimeout) {
}
