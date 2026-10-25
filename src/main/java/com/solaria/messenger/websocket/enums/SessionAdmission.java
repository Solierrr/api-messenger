package com.solaria.messenger.websocket.enums;

/** Resultado da admissão local, depois de validar o JWT e a atividade na Auth. */
public enum SessionAdmission {
    ADMITTED,
    SESSION_LIMIT,
    REJECTED
}
