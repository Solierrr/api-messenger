package com.solaria.messenger.core.security.jwt;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;

/**
 * Decoder JWT único, compartilhado pela chain HTTP e pelo CONNECT STOMP:
 * assinatura RS256 via JWKS, issuer, expiração e token_type.
 */
@Configuration
// Liga as classes com @ConfigurationProperties como Beans
@EnableConfigurationProperties(JwtProperties.class)
public class JwtDecoderConfig {

    /** Substitui o validador padrão por issuer + expiração ({@code createDefaultWithIssuer}) e tipo de token. */
    @Bean
    public JwtDecoder userJwtDecoder(JwtProperties jwtProperties) {
        // Define URL JWKS onde o decoder busca uma chave pública para verificar a assinatura RS256
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(jwtProperties.getJwkSetUri()).build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(jwtProperties.getIssuer()),
                new AccessTokenTypeValidator()));
        return decoder;
    }
}
