package com.solaria.messenger.core.integration.auth.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;

/**
 * Configuração do cliente HTTP da integração com a api-auth
 *
 * <p>Principais beans disponibilizados:</p>
 * <ul>
 *     <li>{@link #authRestClient(AuthClientProperties)} -> cliente HTTP apontando para a api-auth.</li>
 * </ul>
 */
@Configuration
@EnableConfigurationProperties(AuthClientProperties.class)
public class AuthClientConfig {

    /** Cliente HTTP da api-auth com os timeouts de conexão e leitura configurados. */
    @Bean
    public RestClient authRestClient(AuthClientProperties properties) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(properties.getConnectTimeout())
                .build();
        JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
        requestFactory.setReadTimeout(properties.getReadTimeout());
        return RestClient.builder()
                .baseUrl(properties.getBaseUrl())
                .requestFactory(requestFactory)
                .build();
    }
}
