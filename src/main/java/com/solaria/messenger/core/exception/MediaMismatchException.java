package com.solaria.messenger.core.exception;

import org.springframework.http.HttpStatus;

/** O arquivo depositado não confere com o anexo da mensagem: outra conversa, outro remetente, outro tamanho ou outro tipo. */
public class MediaMismatchException extends BusinessException {

    public MediaMismatchException(String message) {
        super(message, HttpStatus.UNPROCESSABLE_CONTENT, "MEDIA_MISMATCH");
    }
}
