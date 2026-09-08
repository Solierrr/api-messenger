package com.solaria.messenger.observability;

import org.springframework.boot.security.autoconfigure.actuate.web.servlet.EndpointRequest;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;

/**
 * {@link SecurityFilterChain} dedicada com prioridade({@code @Order(0)}) para os endpoints do Actuator
 *
 * <p>
 * Sem isso {@code /actuator/**} cairia nas chains de negócio
 * exigiria JWT então as probes do Kubernetes/load balancers receberiam {@code 401}
 * </p>
 *
 * <p>
 * Classe separada de {@code SecurityConfig} 
 * </p>
 */
@Configuration
public class ActuatorSecurityConfig {

    /**
     * Chain de segurança aplicada apenas aos endpoints do Actuator
     *
     * @return chain que libera {@code /actuator/health} e {@code /actuator/info}
     */
    @Bean
    @Order(0)
    public SecurityFilterChain actuatorSecurityFilterChain(HttpSecurity http) throws Exception {
        http
                // restringe esta chain apenas aos endpoints do Actuator
                .securityMatcher(EndpointRequest.toAnyEndpoint())
                // health/info liberados para probes e checagens externas
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers(EndpointRequest.to("health", "info")).permitAll()
                        .anyRequest().denyAll())
                .csrf(csrf -> csrf.disable())
                .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS));
        return http.build();
    }
}
