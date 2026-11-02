package com.solaria.messenger.core.exception;

import org.springframework.http.HttpStatus;

/** A mensagem cita um arquivo que não está no armazenamento: nunca foi enviado ou já passou do prazo. */
public class MediaNotUploadedException extends BusinessException {

    public MediaNotUploadedException() {
        super("O arquivo do anexo não foi enviado ou já não está disponível.", HttpStatus.UNPROCESSABLE_CONTENT, "MEDIA_NOT_UPLOADED");
    }
}
