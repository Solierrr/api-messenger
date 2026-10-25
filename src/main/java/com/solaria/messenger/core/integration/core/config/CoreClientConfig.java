package com.solaria.messenger.core.integration.core.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;


/**
 * Cliente HTTP para a api-core
 *
 * <p>Com {@code serviceKey} configurada, toda chamada leva o header {@code X-Service-Key},
 * exigido pela api-core nas rotas {@code /internal/**}</p>
 */
@Configuration
@EnableConfigurationProperties(CoreClientProperties.class)
public class CoreClientConfig {

    @Bean
    public RestClient persistenceRestClient(CoreClientProperties properties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout((int) properties.connectTimeout().toMillis());
        requestFactory.setReadTimeout((int) properties.readTimeout().toMillis());

        RestClient.Builder builder = RestClient.builder()
                .baseUrl(properties.baseUrl())
                .requestFactory(requestFactory);
        if (properties.serviceKey() != null && !properties.serviceKey().isBlank()) {
            builder.defaultHeader("X-Service-Key", properties.serviceKey());
        }
        return builder.build();
    }
}
