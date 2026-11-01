package com.solaria.messenger.websocket.error;

import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.messaging.converter.MessageConversionException;
import org.springframework.messaging.handler.annotation.MessageExceptionHandler;
import org.springframework.messaging.handler.annotation.support.MethodArgumentNotValidException;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.validation.BindingResult;
import org.springframework.web.bind.annotation.ControllerAdvice;

import com.solaria.messenger.core.exception.BusinessException;
import com.solaria.messenger.core.security.rbac.RbacAuthorizationService;
import com.solaria.messenger.websocket.dto.response.WsErrorPayload;
import com.solaria.messenger.websocket.security.StompDestinations;

/**
 * Tratamento centralizado dos erros dos handlers {@code @MessageMapping}.
 *
 * <p>
 * O cliente é avisado em {@code /topic/users.{userId}.errors} e a conexão permanece aberta.
 * </p>
 */
@ControllerAdvice
public class StompErrorHandler {

    private static final Logger log = LoggerFactory.getLogger(StompErrorHandler.class);

    private final RbacAuthorizationService rbac;
    private final SimpMessagingTemplate messaging;

    public StompErrorHandler(RbacAuthorizationService rbac, SimpMessagingTemplate messaging) {
        this.rbac = rbac;
        this.messaging = messaging;
    }

    /** Erro de negócio vira payload com o mesmo código do REST e a correlação do cliente, quando houver */
    @MessageExceptionHandler(BusinessException.class)
    public void handleBusinessException(BusinessException ex) {
        sendError(WsErrorPayload.builder()
                .code(ex.getErrorCode())
                .message(ex.getMessage())
                .clientMessageId(correlatedClientMessageId(ex))
                .build(), "erro de negocio");
    }

    /** Falha de Bean Validation no payload lista os campos inválidos */
    @MessageExceptionHandler(MethodArgumentNotValidException.class)
    public void handleValidationException(MethodArgumentNotValidException ex) {
        BindingResult bindingResult = ex.getBindingResult();
        String detail = (bindingResult != null)
                ? bindingResult.getFieldErrors().stream()
                        .map(fe -> fe.getField() + ": " + fe.getDefaultMessage())
                        .collect(Collectors.joining("; "))
                : ex.getMessage();

        sendError(WsErrorPayload.builder()
                .code("VALIDATION_ERROR")
                .message("Erro de validação nos campos da requisição" + (detail == null || detail.isBlank() ? "." : ": " + detail))
                .build(), "erro de validação");
    }

    /**
     * Corpo do SEND que não é JSON válido
     * comando é descartado e ocliente é avisado no tópico privado de erros
     */
    @MessageExceptionHandler(MessageConversionException.class)
    public void handleMalformedPayload(MessageConversionException ex) {
        log.warn("Corpo STOMP malformado ou ilegível: {}", ex.getMessage());
        sendError(WsErrorPayload.builder()
                .code("MALFORMED_REQUEST")
                .message("Corpo da requisição malformado ou ilegível.")
                .build(), "erro de corpo malformado");
    }

    /** segurnaça extra para possíveis conflitos de sequência  */
    @MessageExceptionHandler(OptimisticLockingFailureException.class)
    public void handleSequenceConflict(OptimisticLockingFailureException ex) {
        sendError(WsErrorPayload.builder()
                .code("CONCURRENT_MODIFICATION")
                .message("Não foi possível reservar a sequência após as tentativas permitidas.")
                .build(), "conflito de sequência");
    }

    /** A correlação só é anexada pelo MessageWriteHelper, já em formato UUID canônico. */
    private static String correlatedClientMessageId(BusinessException ex) {
        return ex.getProperties().get("clientMessageId") instanceof String clientMessageId ? clientMessageId : null;
    }

    /** Erros de comando vão para o tópico privado do user */
    private void sendError(WsErrorPayload payload, String kind) {
        try {
            messaging.convertAndSend(StompDestinations.userTopic(rbac.currentUserId(), "errors"), payload);
        } catch (Exception sendEx) {
            log.warn("Falha ao enviar {} WS para o tópico de erros", kind, sendEx);
        }
    }
}
