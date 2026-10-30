package com.solaria.messenger.rest.observability;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.http.server.observation.ServerRequestObservationContext;

import io.micrometer.observation.ObservationPredicate;
import io.micrometer.tracing.Tracer;
import jakarta.servlet.DispatcherType;

/**
 * Configura a observabilidade das requisições HTTP: header de traceId e descarte do tráfego do Actuator
 *
 * <p>
 * Sem o {@link TraceIdResponseFilter}, web e mobile não recebem o traceId para levar ao suporte
 * -> a request não é localizada no Grafana
 * </p>
 *
 * <p>
 * {@link #noActuatorObservations()} -> descarta spans e métricas do tráfego de probe em {@code /actuator/**},
 * reduzindo ruído e custo
 * </p>
 */
@Configuration
public class HttpObservabilityConfig {

    /**
     * Registra o {@link TraceIdResponseFilter} dentro do {@code ServerHttpObservationFilter}
     *
     * @param tracerProvider fornece o tracer quando o tracing está habilitado
     */
    @Bean
    public FilterRegistrationBean<TraceIdResponseFilter> traceIdResponseFilterRegistration(
            ObjectProvider<Tracer> tracerProvider) {
        FilterRegistrationBean<TraceIdResponseFilter> registration =
                new FilterRegistrationBean<>(new TraceIdResponseFilter(tracerProvider));
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 2);
        registration.setDispatcherTypes(DispatcherType.REQUEST, DispatcherType.ASYNC, DispatcherType.ERROR);
        registration.addUrlPatterns("/*");
        return registration;
    }

    /**
     * Evita gerar métricas e traces para as requisições do Actuator
     */
    @Bean
    public ObservationPredicate noActuatorObservations() {
        return (name, context) -> {
            if (context instanceof ServerRequestObservationContext serverContext) {
                String uri = serverContext.getCarrier().getRequestURI();
                return uri == null || !uri.startsWith("/actuator");
            }
            return true;
        };
    }
}
