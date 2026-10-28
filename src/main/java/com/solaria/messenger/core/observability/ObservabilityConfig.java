package com.solaria.messenger.core.observability;

import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskDecorator;
import org.springframework.core.task.support.ContextPropagatingTaskDecorator;

/**
 * Configuração de observabilidade comum aos dois transportes
 *
 * <p>Concentra na camada de infra os beans que ajustam o comportamento de
 * Micrometer / Micrometer Tracing / OpenTelemetry, sem misturar observabilidade com negócio.
 * O que é específico de HTTP fica em {@link com.solaria.messenger.rest.observability.HttpObservabilityConfig}.</p>
 */
@Configuration
public class ObservabilityConfig {

    /**
     * Mantém o contexto de observabilidade quando outra thread executa a tarefa
     */
    @Bean
    @ConditionalOnClass(name = "io.micrometer.context.ContextSnapshot")
    public TaskDecorator contextPropagatingTaskDecorator() {
        return new ContextPropagatingTaskDecorator();
    }
}
