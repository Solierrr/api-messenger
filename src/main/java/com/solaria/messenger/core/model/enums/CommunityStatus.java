package com.solaria.messenger.core.model.enums;

/**
 * Status de uma comunidade de projeto
 *
 * <ul>
 *   <li>{@code ACTIVE} -> operacional -> aceita novos membros e novas conversas</li>
 *   <li>{@code ARCHIVED} -> bloqueia administração e mensagens; preserva leitura,
 *       estado pessoal, saída voluntária e reativação explícita autorizada</li>
 * </ul>
 */
public enum CommunityStatus {
    ACTIVE,
    ARCHIVED
}
