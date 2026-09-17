package com.solaria.messenger.model.enums;

/**
 * Tipo de conversa.
 *
 * <ul>
 *   <li>{@code DIRECT} -> conversa pessoa-a-pessoa (exatamente 2 participantes)</li>
 *   <li>{@code GROUP} -> conversa em grupo (2+ participantes)
 *       pode ou não estar vinculada a uma {@link ProjectCommunity} ({@code communityId})</li>
 *   <li>{@code CHAT_BOT} -> conversa de um único usuário com o chatbot</li>
 * </ul>
 */
public enum ConversationType {
    DIRECT,
    GROUP,
    CHAT_BOT
}
