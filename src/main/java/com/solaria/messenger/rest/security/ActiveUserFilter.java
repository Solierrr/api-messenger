package com.solaria.messenger.rest.security;

import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ProblemDetail;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.filter.OncePerRequestFilter;

import com.solaria.messenger.core.exception.BusinessException;
import com.solaria.messenger.rest.error.ProblemDetailFactory;
import com.solaria.messenger.rest.observability.HttpObservationErrors;
import com.solaria.messenger.core.service.UserAccessService;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import tools.jackson.databind.ObjectMapper;

/**
 * Filtro que valida o usuário do JWT antes de a requisição chegar ao controller
 *
 * <p>Confere se o subject do token é um UUID canônico e se o usuário está ativo na api-auth, consultando
 * {@link UserAccessService} a cada requisição. Usuário inativo ou inexistente recebe erro no formato
 * {@code ProblemDetail}, escrito diretamente na resposta, pois o filtro roda antes do Spring MVC.</p>
 *
 * <p>Não se aplica à documentação OpenAPI nem ao handshake WebSocket.</p>
 */
public class ActiveUserFilter extends OncePerRequestFilter {

    private static final Logger log = LoggerFactory.getLogger(ActiveUserFilter.class);
    private static final Pattern CANONICAL_UUID = Pattern.compile(
            "[0-9a-fA-F]{8}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{4}-[0-9a-fA-F]{12}");

    private final UserAccessService userAccessService;
    private final ProblemDetailAuthenticationEntryPoint authenticationEntryPoint;
    private final ProblemDetailFactory problemDetailFactory;
    private final ObjectMapper objectMapper;

    public ActiveUserFilter(UserAccessService userAccessService,
            ProblemDetailAuthenticationEntryPoint authenticationEntryPoint,
            ProblemDetailFactory problemDetailFactory,
            ObjectMapper objectMapper) {
        this.userAccessService = userAccessService;
        this.authenticationEntryPoint = authenticationEntryPoint;
        this.problemDetailFactory = problemDetailFactory;
        this.objectMapper = objectMapper;
    }

    /** Documentação OpenAPI e o handshake WebSocket não consultam a Auth por requisição. */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath();
        return path.equals("/v3/api-docs") || path.startsWith("/v3/api-docs/")
                || path.equals("/swagger-ui.html") || path.equals("/swagger-ui") || path.startsWith("/swagger-ui/")
                || path.equals("/ws") || path.equals("/messaging/ws");
    }

    /** Recusa JWT com subject não canônico e usuário inativo ou inexistente na Auth antes do controller. */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (!(authentication instanceof JwtAuthenticationToken jwtAuthentication)) {
            filterChain.doFilter(request, response);
            return;
        }

        String subject = jwtAuthentication.getToken().getSubject();
        if (subject == null || !CANONICAL_UUID.matcher(subject).matches()) {
            authenticationEntryPoint.commence(request, response,
                    new BadCredentialsException("O subject do JWT não é um UUID canônico."));
            return;
        }

        try {
            userAccessService.requireActiveActor(UUID.fromString(subject));
        } catch (BusinessException ex) {
            writeBusinessError(request, response, ex);
            return;
        }
        filterChain.doFilter(request, response);
    }

    /** Escreve o erro como ProblemDetail direto na resposta, pois o filtro roda antes do Spring MVC. */
    private void writeBusinessError(HttpServletRequest request, HttpServletResponse response, BusinessException ex)
            throws IOException {
        HttpObservationErrors.mark(request, ex);
        if (ex.getStatus().is5xxServerError()) {
            log.error("Falha {} em {} {}: {}", ex.getStatus().value(), request.getMethod(), request.getRequestURI(),
                    ex.getMessage(), ex);
        } else {
            log.warn("Erro {} em {} {}: {}", ex.getStatus().value(), request.getMethod(), request.getRequestURI(),
                    ex.getMessage());
        }

        ProblemDetail problem = problemDetailFactory.create(ex.getStatus(), ex.getMessage(), ex.getErrorCode(),
                ex.getProperties(), request);
        response.setStatus(ex.getStatus().value());
        response.setContentType("application/problem+json");
        response.setCharacterEncoding("UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(problem));
    }
}
