package com.solaria.messenger.model.enums;

/**
 * Status de uma comunidade de projeto
 *
 * <ul>
 *   <li>{@code ACTIVE} -> operacional -> aceita novos membros e novas conversas</li>
 *   <li>{@code ARCHIVED} -> somente leitura -> não aceita novos membros nem novas conversas</li>
 * </ul>
 */
public enum CommunityStatus {
    ACTIVE,
    ARCHIVED
}
