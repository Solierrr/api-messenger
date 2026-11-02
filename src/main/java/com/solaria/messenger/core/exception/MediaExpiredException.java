package com.solaria.messenger.core.exception;

import org.springframework.http.HttpStatus;

/** O arquivo passou do prazo de retenção, ou nunca existiu: já não pode ser retirado. */
public class MediaExpiredException extends BusinessException {

    public MediaExpiredException() {
        super("A mídia não está mais disponível.", HttpStatus.GONE, "MEDIA_EXPIRED");
    }
}
