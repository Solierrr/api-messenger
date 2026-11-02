package com.solaria.messenger.core.port;

/** Porta de saída para avisar a presença de quem passou a dividir uma conversa. */
public interface PresencePublisher {

    /** Manda a cada participante autorizado da conversa quem, entre os outros, está online. */
    void conversationJoined(String conversationId);
}
