package com.solaria.messenger.model.enums;

/**
 * Tipos de mensagem, a partir da origem e destino da mesma
 *
 * <ul>
 *   <li>{@code USER_TO_USER} -> mensagem numa conversa {@code DIRECT}</li>
 *   <li>{@code USER_TO_GROUP} -> mensagem numa conversa {@code GROUP}</li>
 *   <li>{@code USER_TO_CHATBOT} -> mensagem do usuário para a LLM</li>
 *   <li>{@code CHATBOT_TO_USER} -> resposta do chatbot, só pode ser inserida em {@code POST /internal/messages}</li>
 * </ul>
 */
public enum MessageType {
    USER_TO_USER,
    USER_TO_GROUP,
    USER_TO_CHATBOT,
    CHATBOT_TO_USER
}
