package com.solaria.messenger.core.integration.core.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Propriedades do cliente HTTP para a api-core 
 */
@ConfigurationProperties(prefix = "app.integration.core")
public record CoreClientProperties(String baseUrl, Duration connectTimeout, Duration readTimeout, String serviceKey) {
}
