package com.solaria.messenger.security;

import java.util.Arrays;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import com.solaria.messenger.security.service.ServiceTokenAuthFilter;
import com.solaria.messenger.security.service.ServiceTokenProperties;
import com.solaria.messenger.security.service.ServiceTokenProvider;

/**
 * Duas SecurityFilterChain, estruturalmente independentes e segregadas
 * por @Order. Mesmo padrão usado em api-persistence
 *
 * @Order(1): rotas -> internal/**; token/jwt de serviço M2M(API->API);
 * HS256/JJWT;
 * segredo compartilhado (SERVICE_JWT_SECRET), sem JWKS.
 *
 * @Order(2): rotas -> JWT de usuário emitido pelo api-auth;
 * resource server RS256/JWKS;
 * sem segredo compartilhado.
 *
 * Diferença em relação a api-persistence: não há RBAC por permissão, e sim por ownership(dono da entidade)
 * usando a claim de authId vindo do JWT
 */

@Configuration

// ativa a integração do Spring Security com Spring MVC
@EnableWebSecurity

@EnableMethodSecurity

// Liga as classes com @ConfigurationProperties como Beans
@EnableConfigurationProperties({ JwtProperties.class, ServiceTokenProperties.class })
public class SecurityConfig {

        private final JwtProperties jwtProperties;

        private final ServiceTokenProvider serviceTokenProvider;

        // Handlers 401/403 compartilhados pelas duas chains
        private final ProblemDetailAuthenticationEntryPoint problemDetailAuthenticationEntryPoint;
        private final ProblemDetailAccessDeniedHandler problemDetailAccessDeniedHandler;

        // Origens permitidas
        @Value("${app.cors.allowed-origins}")
        private String allowedOrigins;

        public SecurityConfig(JwtProperties jwtProperties,
                        ServiceTokenProvider serviceTokenProvider,
                        ProblemDetailAuthenticationEntryPoint problemDetailAuthenticationEntryPoint,
                        ProblemDetailAccessDeniedHandler problemDetailAccessDeniedHandler) {
                this.jwtProperties = jwtProperties;
                this.serviceTokenProvider = serviceTokenProvider;
                this.problemDetailAuthenticationEntryPoint = problemDetailAuthenticationEntryPoint;
                this.problemDetailAccessDeniedHandler = problemDetailAccessDeniedHandler;
        }

        // Registra o objeto retornado como um Bean
        @Bean
        // @Order(1)-> esta chain é avaliada ANTES da chain @Order(2), por path mais específico
        @Order(1)
        public SecurityFilterChain internalSecurityFilterChain(HttpSecurity http) throws Exception {
                http
                                // Restringe essa chain apenas para /internal/**
                                .securityMatcher("/internal/**")
                                // CSRF desabilitado
                                .csrf(csrf -> csrf.disable())
                                // HttpSession não é usado/criado
                                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                                // CorsConfigurationSource é a mesma para as duas chains
                                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                                // adiciona filtro de token antes de qualquer verificação de autorização
                                .addFilterBefore(new ServiceTokenAuthFilter(serviceTokenProvider),
                                                UsernamePasswordAuthenticationFilter.class)
                                .authorizeHttpRequests(auth -> auth
                                                // Fora do filtro de Bearer token
                                                // mint autentica via clientSecret no corpo;
                                                // refresh autentica via posse de um refresh token válido;
                                                .requestMatchers(HttpMethod.POST,
                                                                "/internal/service-tokens",
                                                                "/internal/service-tokens/refresh")
                                                .permitAll()
                                                // Todo o resto de /internal/** exige autenticação
                                                .anyRequest().authenticated())
                                // Garante que exceptions usem o formato de ProblemDetail
                                .exceptionHandling(e -> e
                                                .authenticationEntryPoint(problemDetailAuthenticationEntryPoint)
                                                .accessDeniedHandler(problemDetailAccessDeniedHandler));
                // Constrói e devolve a SecurityFilterChain configurada acima como o bean deste método
                return http.build();
        }

        // Registra o objeto retornado como um Bean
        @Bean
        // @Order(2)-> avaliada depois da chain de /internal/**
        // qualquer requisição que não bateu com /internal/** cai aqui.
        @Order(2)
        public SecurityFilterChain resourceServerSecurityFilterChain(HttpSecurity http) throws Exception {
                http
                                // CSRF desabilitado
                                .csrf(csrf -> csrf.disable())
                                // HttpSession não é usado/criado. toda request se autentica sozinha pelo header
                                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                                // CorsConfigurationSource é a mesma para as duas chains
                                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                                // define que a aplicação é um Resource Server OAuth2 e receberá tokens Bearer (JWT) nas requisições.
                                .oauth2ResourceServer(oauth2 -> oauth2
                                                // decoder personalizado para validar jwt (JWKS + validador de issuer + token_type)
                                                .jwt(jwt -> jwt.decoder(userJwtDecoder()))
                                                // Duplicação de exception pois oauth2ResourceServer() tem prioridade sobre exceptionHandling
                                                .authenticationEntryPoint(problemDetailAuthenticationEntryPoint)
                                                .accessDeniedHandler(problemDetailAccessDeniedHandler))
                                .authorizeHttpRequests(auth -> auth
                                                // Docs OpenAPI/Swagger ficam públicas
                                                .requestMatchers("/v3/api-docs/**", "/swagger-ui/**",
                                                                "/swagger-ui.html")
                                                .permitAll()
                                                // todos os outros endpoints exigem um jwt de usuário válido
                                                .anyRequest().authenticated())
                                // Garante que exceptions usem o formato de ProblemDetail
                                .exceptionHandling(e -> e
                                                .authenticationEntryPoint(problemDetailAuthenticationEntryPoint)
                                                .accessDeniedHandler(problemDetailAccessDeniedHandler));
                // Constrói e devolve a SecurityFilterChain configurada acima como o bean deste método.
                return http.build();
        }
        // Registra o objeto retornado como um Bean
        @Bean
        public JwtDecoder userJwtDecoder() {

                // Define URL JWKS onde o decoder busca uma chave pública para verificar a assinatura RS256
                NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(jwtProperties.getJwkSetUri()).build();

                // Substitui o validador padrão do decoder, por 2 validadores
                // createDefaultWithIssuer -> valida emissor
                // AccessTokenTypeValidator -> valida tipo de token
                decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                                JwtValidators.createDefaultWithIssuer(jwtProperties.getIssuer()),
                                new AccessTokenTypeValidator()));
                return decoder;
        }

        // Bean único de configuração CORS, compartilhado pelas duas SecurityFilterChain
        @Bean
        public CorsConfigurationSource corsConfigurationSource() {
                // origens permitidas (app.cors.allowed-origins é uma string CSV)
                List<String> origins = Arrays.stream(allowedOrigins.split(","))
                                .map(String::trim)
                                .filter(origin -> !origin.isBlank())
                                .toList();

                // Objeto de configuração CORS padrão do Spring (origins/métodos/headers permitidos).
                CorsConfiguration configuration = new CorsConfiguration();
                // origens permitidas
                configuration.setAllowedOrigins(origins);
                // Métodos HTTP usados pela API
                configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
                // headers permitidos
                configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "apikey"));
                // Aplica esta CorsConfiguration a todos os paths ("/**")
                UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
                source.registerCorsConfiguration("/**", configuration);
                return source;
        }
}
