package com.solaria.messenger.core.security.jwt;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Propriedades do JWT de usuário (resource server RS256 + JWKS), emitido pelo api-auth.
 */

// Habilita essa classe com as variaveis em application.properties com prefixo app.jwt
@ConfigurationProperties(prefix = "app.jwt")
public class JwtProperties {

    // URL do endpoint JWKS do api-auth
    private String jwkSetUri;

    // Issuer esperado no token de usuário
    private String issuer;

    /** URL do JWKS usada para validar a assinatura RS256. */
    public String getJwkSetUri() {
        return jwkSetUri;
    }

    /** Define a URL do JWKS. */
    public void setJwkSetUri(String jwkSetUri) {
        this.jwkSetUri = jwkSetUri;
    }

    /** Issuer esperado no JWT. */
    public String getIssuer() {
        return issuer;
    }

    /** Define o issuer esperado no JWT. */
    public void setIssuer(String issuer) {
        this.issuer = issuer;
    }
}
