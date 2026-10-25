package com.solaria.messenger.core.integration.auth.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/**
 * Propriedades da integração com a api-auth, mapeadas do prefixo {@code app.integration.auth}.
 */
@ConfigurationProperties(prefix = "app.integration.auth")
public class AuthClientProperties {

    // URL de api-auth
    private String baseUrl;

    // timeout de conexão da chamada HTTP a api-auth
    private Duration connectTimeout = Duration.ofSeconds(2);

    // timeout de leitura da chamada HTTP a api-auth
    private Duration readTimeout = Duration.ofSeconds(3);

    /** URL base da api-auth. */
    public String getBaseUrl() {
        return baseUrl;
    }

    /** Define a URL base da api-auth. */
    public void setBaseUrl(String baseUrl) {
        this.baseUrl = baseUrl;
    }

    /** Timeout de conexão com a api-auth. */
    public Duration getConnectTimeout() {
        return connectTimeout;
    }

    /** Define o timeout de conexão com a api-auth. */
    public void setConnectTimeout(Duration connectTimeout) {
        this.connectTimeout = connectTimeout;
    }

    /** Timeout de leitura da resposta da api-auth. */
    public Duration getReadTimeout() {
        return readTimeout;
    }

    /** Define o timeout de leitura da resposta da api-auth. */
    public void setReadTimeout(Duration readTimeout) {
        this.readTimeout = readTimeout;
    }
}
