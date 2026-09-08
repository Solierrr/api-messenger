package com.solaria.messenger.security;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;

import com.solaria.messenger.exception.handler.ProblemDetailFactory;
import com.solaria.messenger.observability.HttpObservationErrors;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import tools.jackson.databind.ObjectMapper;

/**
 * Classe responsável por montar corpos de Jsons de erro, especificos para RBAC.
 *
 * <p>O 403 é produzido na cadeia de filtros ]
 *  marca a observação do servidor como erro e loga correlacionado ao trace
 * </p>
 */
@Component
public class ProblemDetailAccessDeniedHandler implements AccessDeniedHandler {

    private static final Logger log = LoggerFactory.getLogger(ProblemDetailAccessDeniedHandler.class);

    private final ProblemDetailFactory problemDetailFactory;
    private final ObjectMapper objectMapper;

    public ProblemDetailAccessDeniedHandler(ProblemDetailFactory problemDetailFactory,
                                             ObjectMapper objectMapper) {
        this.problemDetailFactory = problemDetailFactory;
        this.objectMapper = objectMapper;
    }


    @Override
    public void handle(HttpServletRequest request, HttpServletResponse response,
                        AccessDeniedException accessDeniedException) throws IOException {

        HttpObservationErrors.mark(request, accessDeniedException);
        log.warn("403 em {} {}: {}", request.getMethod(), request.getRequestURI(),
                accessDeniedException.getMessage());

        // cria corpo do json a partir da classe ProblemDetail
        ProblemDetail problem = problemDetailFactory.create(
                HttpStatus.FORBIDDEN,
                "Acesso negado: você não tem permissão para acessar este recurso.",
                "ACCESS_DENIED",
                null,
                request);
        // Escreve status/headers/corpo diretamente na resposta pois esse handler fica na camada de filtro, antes de spring MVC
        response.setStatus(HttpStatus.FORBIDDEN.value());
        response.setContentType("application/problem+json");
        response.setCharacterEncoding("UTF-8");
        // Serializa o ProblemDetail para JSON e escreve no corpo da resposta.
        response.getWriter().write(objectMapper.writeValueAsString(problem));
    }
}
