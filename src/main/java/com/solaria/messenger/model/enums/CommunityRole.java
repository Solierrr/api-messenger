package com.solaria.messenger.model.enums;

/**
 * cargo de um membro de uma comunidade
 * define quais operações o usuário pode executar 
 *
 * <ul>
 *   <li>{@code OWNER} -> quem criou a comunidade, Faz tudo que o {@code ADMIN} faz além de
 *       controlar os admins e arquivar a comunidade</li>
 *   <li>{@code ADMIN} -> adiciona/remove membros comuns</li>
 *   <li>{@code MEMBER} -> participa da comunidade, vê os membros e inicia conversas
 *       em grupo com outros membros.</li>
 * </ul>
 */
public enum CommunityRole {
    OWNER,
    ADMIN,
    MEMBER
}
