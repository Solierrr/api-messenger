package com.solaria.messenger.rest.security;

import java.util.Arrays;
import java.util.List;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Profile;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.web.authentication.BearerTokenAuthenticationFilter;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import tools.jackson.databind.ObjectMapper;

import com.solaria.messenger.rest.error.ProblemDetailFactory;
import com.solaria.messenger.core.service.UserAccessService;

/**
 * Fronteiras de segurança HTTP do Messenger
 * Actuator é restrito em sua configuração própria;
 * /internal/** usa isolamento de rede e a chain de usuário valida o JWT com o decoder comum de
 * {@link com.solaria.messenger.core.security.jwt.JwtDecoderConfig}. 
 * O proxy /dev/login e sua chain só existem no perfil dev.
 */

@Configuration

// ativa a integração do Spring Security com Spring MVC
@EnableWebSecurity
public class SecurityConfig {

        // Handlers 401/403 usados pela chain @Order(2)
        private final ProblemDetailAuthenticationEntryPoint problemDetailAuthenticationEntryPoint;
        private final ProblemDetailAccessDeniedHandler problemDetailAccessDeniedHandler;

        // Origens permitidas
        @Value("${app.cors.allowed-origins}")
        private String allowedOrigins;

        public SecurityConfig(ProblemDetailAuthenticationEntryPoint problemDetailAuthenticationEntryPoint,
                        ProblemDetailAccessDeniedHandler problemDetailAccessDeniedHandler) {
                this.problemDetailAuthenticationEntryPoint = problemDetailAuthenticationEntryPoint;
                this.problemDetailAccessDeniedHandler = problemDetailAccessDeniedHandler;
        }

        /** Cria o filtro de usuário ativo com o registro servlet desabilitado, para que ele rode só dentro da chain de segurança. */
        @Bean
        public FilterRegistrationBean<ActiveUserFilter> activeUserFilterRegistration(
                        UserAccessService userAccessService,
                        ProblemDetailFactory problemDetailFactory,
                        ObjectMapper objectMapper) {
                FilterRegistrationBean<ActiveUserFilter> registration = new FilterRegistrationBean<>(
                                new ActiveUserFilter(userAccessService, problemDetailAuthenticationEntryPoint,
                                                problemDetailFactory, objectMapper));
                registration.setEnabled(false);
                return registration;
        }

        /** O proxy de login só existe em dev e não exige um token anterior para obter o primeiro JWT. */
        @Bean
        @Profile("dev")
        @Order(1)
        public SecurityFilterChain devLoginSecurityFilterChain(HttpSecurity http) {
                return http.securityMatcher("/dev/login")
                                .csrf(csrf -> csrf.disable())
                                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll())
                                .build();
        }

        // Registra o objeto retornado como um Bean
        @Bean
        // @Order(1)-> esta chain é avaliada ANTES da chain @Order(2), por path mais específico
        @Order(1)
        public SecurityFilterChain internalSecurityFilterChain(HttpSecurity http) {
                http
                                // Restringe essa chain apenas para /internal/**
                                .securityMatcher("/internal/**")
                                // CSRF desabilitado 
                                .csrf(csrf -> csrf.disable())
                                // HttpSession não é usado/criado
                                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                                // CorsConfigurationSource é a mesma para as duas chains
                                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                                // Rotas internas de rede-> sem autenticação de aplicação
                                .authorizeHttpRequests(auth -> auth.anyRequest().permitAll());
                // Constrói e devolve a SecurityFilterChain configurada acima como o bean deste método
                return http.build();
        }

        // Registra o objeto retornado como um Bean
        @Bean
        // @Order(2)-> avaliada depois da chain de /internal/**
        // qualquer requisição que não bateu com /internal/** cai aqui.
        @Order(2)
        public SecurityFilterChain resourceServerSecurityFilterChain(HttpSecurity http,
                        FilterRegistrationBean<ActiveUserFilter> activeUserFilterRegistration,
                        JwtDecoder userJwtDecoder) {
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
                                                .jwt(jwt -> jwt.decoder(userJwtDecoder))
                                                // Duplicação de exception pois oauth2ResourceServer() tem prioridade sobre exceptionHandling
                                                .authenticationEntryPoint(problemDetailAuthenticationEntryPoint)
                                                .accessDeniedHandler(problemDetailAccessDeniedHandler))
                                .addFilterAfter(activeUserFilterRegistration.getFilter(),
                                                BearerTokenAuthenticationFilter.class)
                                .authorizeHttpRequests(auth -> auth
                                                // Docs OpenAPI/Swagger ficam públicas
                                                .requestMatchers("/v3/api-docs/**", "/swagger-ui/**",
                                                                "/swagger-ui.html")
                                                .permitAll()
                                                // autentificação acontece antes, durante a conexão da sessão
                                                .requestMatchers("/ws", "/messaging/ws").permitAll()
                                                // todos os outros endpoints exigem um jwt de usuário válido
                                                .anyRequest().authenticated())
                                // Garante que exceptions usem o formato de ProblemDetail
                                .exceptionHandling(e -> e
                                                .authenticationEntryPoint(problemDetailAuthenticationEntryPoint)
                                                .accessDeniedHandler(problemDetailAccessDeniedHandler)) ;
                // Constrói e devolve a SecurityFilterChain configurada acima como o bean deste método.
                return http.build();
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
                // expoe o X-Trace-Id para poder ler o traceId 
                configuration.setExposedHeaders(List.of("X-Trace-Id", "X-Conversation-Sync-Epoch", "X-Conversation-Sync-Version"));
                // Aplica esta CorsConfiguration a todos os paths ("/**")
                UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
                source.registerCorsConfiguration("/**", configuration);
                return source;
        }
}
