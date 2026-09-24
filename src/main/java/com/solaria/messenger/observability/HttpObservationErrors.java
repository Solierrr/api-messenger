package com.solaria.messenger.observability;

import io.micrometer.observation.Observation;

import org.springframework.web.filter.ServerHttpObservationFilter;

import jakarta.servlet.http.HttpServletRequest;

/**
 * Marca a requisição HTTP atual como erro quando uma exceção já foi tratada pela aplicação
 *
 * <p>
 * A classe apenas atualiza a observabilidade da request
 * não altera a resposta HTTP nem o tratamento da exceção -> função dos handlers de exception
 * </p>
 *
 */
public final class HttpObservationErrors {

    /**
     * Identifica a observação HTTP criada para a request atual pelo
     * {@code ServerHttpObservationFilter}.
     */
    private static final String OBSERVATION_ATTRIBUTE =
            "org.springframework.web.filter.ServerHttpObservationFilter.observation";

    private HttpObservationErrors() {
    }

    /**
     * Registra uma exceção na observação da request atual.
     *
     * <p>
     * Se a observação estiver disponível -> {@code observation.error(ex)} 
     * dispara os handlers {@code onError} -> status do span vira {@code ERROR} + evento de exceção
     * se não -> tenta {@code context.setError(ex)} (só popula a KeyValue {@code exception}).
     * </p>
     *
     * <p>Sem request, sem exception ou sem observação -> no-op</p>
     */
    public static void mark(HttpServletRequest request, Throwable error) {
        if (request == null || error == null) {
            return;
        }

        if (request.getAttribute(OBSERVATION_ATTRIBUTE) instanceof Observation observation) {
            observation.error(error);
            return;
        }

        // Mesmo sem a observação principal, tenta registrar a exceção no contexto da request
        ServerHttpObservationFilter.findObservationContext(request)
                .ifPresent(context -> context.setError(error));
    }
}
