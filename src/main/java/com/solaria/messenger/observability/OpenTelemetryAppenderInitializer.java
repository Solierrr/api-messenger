package com.solaria.messenger.observability;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.stereotype.Component;

import io.opentelemetry.api.OpenTelemetry;
import io.opentelemetry.instrumentation.logback.appender.v1_0.OpenTelemetryAppender;

/**
 * Liga o appender OTEL do Logback ao {@link OpenTelemetry} da aplicação
 *
 * <p>
 * Se o {@link OpenTelemetry} não estiver disponível, a aplicação segue normal e o appender OTEL fica inutilizado
 * </p>
 *
 */
@Component
public class OpenTelemetryAppenderInitializer implements ApplicationListener<ApplicationReadyEvent> {

    private final ObjectProvider<OpenTelemetry> openTelemetryProvider;

    public OpenTelemetryAppenderInitializer(ObjectProvider<OpenTelemetry> openTelemetryProvider) {
        this.openTelemetryProvider = openTelemetryProvider;
    }

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        OpenTelemetry openTelemetry = openTelemetryProvider.getIfAvailable();
        if (openTelemetry != null) {
            OpenTelemetryAppender.install(openTelemetry);
        }
    }
}
