package com.solaria.messenger.rest.observability;

import java.io.IOException;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.web.filter.OncePerRequestFilter;

import io.micrometer.tracing.Span;
import io.micrometer.tracing.Tracer;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Copia o {@code traceId} do span ativo para o header de resposta {@code X-Trace-Id}
 *
 * <p>
 * Permite que web/mobile devolvam o traceId ao suporte -> localiza a request no Grafana
 * </p>
 *
 * <p>
 * Cobre 2xx, erros do advice, 401/403 da segurança e o dispatch {@code /error}
 * </p>
 *
 * <p>
 * Sem {@link Tracer} (tracing desligado) ou sem span ativo, o filtro não faz nada
 * </p>
 *
 * <p>Registrado em {@link HttpObservabilityConfig#traceIdResponseFilterRegistration}</p>
 */
public class TraceIdResponseFilter extends OncePerRequestFilter {

    /** Header de resposta com o {@code traceId} */
    public static final String TRACE_ID_HEADER = "X-Trace-Id";

    // bean Tracer não existe quando o tracing está desligado
    private final ObjectProvider<Tracer> tracerProvider;

    public TraceIdResponseFilter(ObjectProvider<Tracer> tracerProvider) {
        this.tracerProvider = tracerProvider;
    }

    /** Expõe o traceId do span atual no cabeçalho de resposta antes de a resposta ser confirmada. */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        Tracer tracer = tracerProvider.getIfAvailable();

        if (tracer != null && !response.isCommitted()) {
            Span span = tracer.currentSpan();

            if (span != null) {
                response.setHeader(TRACE_ID_HEADER, span.context().traceId());
            }
        }
        filterChain.doFilter(request, response);
    }
}
