package com.solaria.messenger.observability;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.core.task.TaskDecorator;
import org.springframework.core.task.support.ContextPropagatingTaskDecorator;
import org.springframework.http.server.observation.ServerRequestObservationContext;

import io.micrometer.observation.ObservationPredicate;
import io.micrometer.tracing.Tracer;
import jakarta.servlet.DispatcherType;

/**
 * Configuração central de observabilidade
 *
 * <p>Concentra na camada de infra os beans que ajustam o comportamento de
 * Micrometer / Micrometer Tracing / OpenTelemetry, sem misturar observabilidade com negócio</p>
 *
 *
 * <ul>
 *     <li>{@link #traceIdResponseFilterRegistration(ObjectProvider)} -> registra o
 *     {@link TraceIdResponseFilter} logo após o filtro de observação do Spring MVC</li>
 *     <li>{@link #noActuatorObservations()} -> descarta observações (spans/métricas) do tráfego
 *     de probe em {@code /actuator/**}</li>
 * </ul>
 */
@Configuration
public class ObservabilityConfig {

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

    /**
     * Mantém o contexto de observabilidade quando outra thread executa a tarefa
     */
    @Bean
    @ConditionalOnClass(name = "io.micrometer.context.ContextSnapshot")
    public TaskDecorator contextPropagatingTaskDecorator() {
        return new ContextPropagatingTaskDecorator();
    }
}
