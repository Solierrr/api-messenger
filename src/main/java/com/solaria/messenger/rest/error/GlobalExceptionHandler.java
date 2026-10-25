package com.solaria.messenger.rest.error;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.method.annotation.HandlerMethodValidationException;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import com.solaria.messenger.core.exception.BusinessException;
import com.solaria.messenger.rest.observability.HttpObservationErrors;

import jakarta.servlet.http.HttpServletRequest;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Classe responsável por centralizar tratamento de exceções
 *
 * Logging/observabilidade -> toda exceção tratada é marcada como erro no tracing
 * Gera um log correlacionado ao trace ->{@code WARN} para 4xx, {@code ERROR} com stack trace para 5xx
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final ProblemDetailFactory factory;

    public GlobalExceptionHandler(ProblemDetailFactory factory) {
        this.factory = factory;
    }

    // Informa à observabilidade que a request teve um erro (span ERROR + exceção)
    private void markObservationError(Throwable ex, HttpServletRequest request) {
        HttpObservationErrors.mark(request, ex);
    }

    // Marca a observação como erro e registra um log correlacionado ao trace
    private void recordError(Throwable ex, HttpStatusCode status, String errorCode, HttpServletRequest request) {
        markObservationError(ex, request);
        String method = (request != null) ? request.getMethod() : "?";
        String uri = (request != null) ? request.getRequestURI() : "?";
        if (status.is5xxServerError()) {
            log.atError().addKeyValue("errorCode", errorCode).log("Falha {} em {} {}", status.value(), method, uri, ex);
        } else {
            log.atWarn().addKeyValue("errorCode", errorCode).log("Erro {} em {} {}: {}", status.value(), method, uri, ex.getMessage());
        }
    }

    /** Converte {@link BusinessException} e subclasses no ProblemDetail com o status e o código da exceção. */
    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ProblemDetail> handleBusinessException(BusinessException ex,
                                                                 HttpServletRequest request) {
        recordError(ex, ex.getStatus(), ex.getErrorCode(), request);

        ProblemDetail body = factory.create(
                ex.getStatus(),
                ex.getMessage(),
                ex.getErrorCode(),
                ex.getProperties(),
                request);
        return ResponseEntity.status(ex.getStatus()).body(body);
    }

    /** Violação de índice único no Mongo vira 409 DATA_INTEGRITY_VIOLATION. */
    @ExceptionHandler(DuplicateKeyException.class)
    public ResponseEntity<ProblemDetail> handleDuplicateKey(DuplicateKeyException ex,
                                                             HttpServletRequest request) {
        recordError(ex, HttpStatus.CONFLICT, "DATA_INTEGRITY_VIOLATION", request);

        ProblemDetail body = factory.create(HttpStatus.CONFLICT,
                "A operação viola uma restrição de unicidade.",
                "DATA_INTEGRITY_VIOLATION",
                null,
                request);
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }

    /** Conflito de versão que escapou dos serviços vira 409 CONCURRENT_MODIFICATION. */
    @ExceptionHandler(OptimisticLockingFailureException.class)
    public ResponseEntity<ProblemDetail> handleOptimisticLock(OptimisticLockingFailureException ex,
                                                              HttpServletRequest request) {
        recordError(ex, HttpStatus.CONFLICT, "CONCURRENT_MODIFICATION", request);

        ProblemDetail body = factory.create(HttpStatus.CONFLICT,
                "O registro foi modificado por outra operação simultânea. Recarregue e tente novamente.",
                "CONCURRENT_MODIFICATION",
                null,
                request);
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }

    /** Negação do Spring Security vira 403; sem este handler o catch-all devolveria 500. */
    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ProblemDetail> handleAccessDenied(AccessDeniedException ex,
                                                             HttpServletRequest request) {
        recordError(ex, HttpStatus.FORBIDDEN, "ACCESS_DENIED", request);

        ProblemDetail body = factory.create(HttpStatus.FORBIDDEN,
                "Você não tem permissão para executar esta ação.",
                "ACCESS_DENIED",
                null,
                request);
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(body);
    }

    /** Erro inesperado vira 500 INTERNAL_ERROR, registrado com stack trace. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleUnexpected(Exception ex, HttpServletRequest request) {
        // registra no span (status ERROR + evento de excecao) com stack trace
        recordError(ex, HttpStatus.INTERNAL_SERVER_ERROR, "INTERNAL_ERROR", request);
        ProblemDetail body = factory.create(HttpStatus.INTERNAL_SERVER_ERROR,
                "Ocorreu um erro interno inesperado. Se persistir, contate o suporte.",
                "INTERNAL_ERROR",
                null,
                request);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(body);
    }

    /** Falha de Bean Validation no corpo vira 400 VALIDATION_ERROR com a lista de campos. */
    @Override
    protected ResponseEntity<Object> handleMethodArgumentNotValid(MethodArgumentNotValidException ex,
                                                                  HttpHeaders headers,
                                                                  HttpStatusCode status,
                                                                  WebRequest request) {
        recordError(ex, HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", servletRequest(request));

        List<Map<String, Object>> errors = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(fe -> {
                    Map<String, Object> entry = new LinkedHashMap<>();
                    entry.put("field", fe.getField());
                    entry.put("message", fe.getDefaultMessage());
                    return entry;
                })
                .toList();
        ProblemDetail body = factory.create(HttpStatus.BAD_REQUEST,
                "Erro de validação nos campos da requisição.",
                "VALIDATION_ERROR",
                Map.of("errors", errors),
                servletRequest(request));
        return new ResponseEntity<>(body, HttpStatus.BAD_REQUEST);
    }

    /** Falha de Bean Validation em parâmetros do método vira 400 VALIDATION_ERROR. */
    @Override
    protected ResponseEntity<Object> handleHandlerMethodValidationException(HandlerMethodValidationException ex,
                                                                            HttpHeaders headers,
                                                                            HttpStatusCode status,
                                                                            WebRequest request) {
        recordError(ex, HttpStatus.BAD_REQUEST, "VALIDATION_ERROR", servletRequest(request));

        List<Map<String, Object>> errors = ex.getAllErrors()
                .stream()
                .map(err -> {
                    Map<String, Object> entry = new LinkedHashMap<>();
                    entry.put("message", err.getDefaultMessage());
                    return entry;
                })
                .toList();
        ProblemDetail body = factory.create(HttpStatus.BAD_REQUEST,
                "Erro de validação nos parâmetros da requisição.",
                "VALIDATION_ERROR",
                Map.of("errors", errors), servletRequest(request));
        return new ResponseEntity<>(body, HttpStatus.BAD_REQUEST);
    }

    /** JSON inválido ou ilegível vira 400 MALFORMED_REQUEST. */
    @Override
    protected ResponseEntity<Object> handleHttpMessageNotReadable(HttpMessageNotReadableException ex,
                                                                  HttpHeaders headers,
                                                                  HttpStatusCode status,
                                                                  WebRequest request) {
        recordError(ex, HttpStatus.BAD_REQUEST, "MALFORMED_REQUEST", servletRequest(request));

        ProblemDetail body = factory.create(HttpStatus.BAD_REQUEST,
                "Corpo da requisição malformado ou ilegível.",
                "MALFORMED_REQUEST",
                null,
                servletRequest(request));
        return new ResponseEntity<>(body, HttpStatus.BAD_REQUEST);
    }

    /** Extrai a requisição servlet do {@link WebRequest}, quando houver. */
    private HttpServletRequest servletRequest(WebRequest request) {
        return (request instanceof ServletWebRequest swr) ? swr.getRequest() : null;
    }
}
