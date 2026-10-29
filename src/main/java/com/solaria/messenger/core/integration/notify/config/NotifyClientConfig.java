package com.solaria.messenger.core.integration.notify.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

/**
 * Cliente HTTP para o api-notify
 *
 * <p>Com {@code serviceKey} configurada, toda chamada leva o header {@code X-Service-Key},
 * exigido pelo api-notify nas rotas {@code /internal/**}</p>
 */
@Configuration
@EnableConfigurationProperties(NotifyClientProperties.class)
public class NotifyClientConfig {

    @Bean
    public RestClient notifyRestClient(NotifyClientProperties properties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout((int) properties.connectTimeout().toMillis());
        requestFactory.setReadTimeout((int) properties.readTimeout().toMillis());

        RestClient.Builder builder = RestClient.builder().requestFactory(requestFactory);
        if (properties.baseUrl() != null && !properties.baseUrl().isBlank()) {
            builder.baseUrl(properties.baseUrl());
        }
        if (properties.serviceKey() != null && !properties.serviceKey().isBlank()) {
            builder.defaultHeader("X-Service-Key", properties.serviceKey());
        }
        return builder.build();
    }
}
