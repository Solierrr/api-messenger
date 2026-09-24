package com.solaria.messenger.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import com.solaria.messenger.integration.PersistenceClientProperties;

/**
 * Cliente HTTP para a api-persistence
 */
@Configuration
@EnableConfigurationProperties(PersistenceClientProperties.class)
public class PersistenceClientConfig {

    @Bean
    public RestClient persistenceRestClient(PersistenceClientProperties properties) {
        SimpleClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
        requestFactory.setConnectTimeout((int) properties.connectTimeout().toMillis());
        requestFactory.setReadTimeout((int) properties.readTimeout().toMillis());

        return RestClient.builder()
                .baseUrl(properties.baseUrl())
                .requestFactory(requestFactory)
                .build();
    }
}
