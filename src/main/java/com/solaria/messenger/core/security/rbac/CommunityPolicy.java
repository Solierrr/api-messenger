package com.solaria.messenger.core.security.rbac;

import java.util.UUID;

import com.solaria.messenger.core.exception.BusinessRuleException;
import com.solaria.messenger.core.exception.UnauthorizedAccessException;
import com.solaria.messenger.core.model.ProjectCommunity;
import com.solaria.messenger.core.model.enums.CommunityStatus;

/** Autorização por papel ({@code OWNER/ADMIN/MEMBER}) na comunidade, compartilhada pelos serviços e pela cascata. */
public final class CommunityPolicy {
    private CommunityPolicy() { }

    /** Exige que o ator seja membro da comunidade. */
    public static void requireMember(ProjectCommunity community, UUID actor) {
        if (!community.isMember(actor)) {
            throw new UnauthorizedAccessException("O usuário autenticado não participa desta comunidade.");
        }
    }

    /** Exige papel OWNER ou ADMIN na comunidade. */
    public static void requireAdmin(ProjectCommunity community, UUID actor) {
        if (!community.isAdmin(actor)) {
            throw new UnauthorizedAccessException("Esta ação exige papel OWNER ou ADMIN na comunidade.");
        }
    }

    /** Exige papel OWNER na comunidade. */
    public static void requireOwner(ProjectCommunity community, UUID actor) {
        if (!community.isOwner(actor)) {
            throw new UnauthorizedAccessException("Esta ação exige papel OWNER na comunidade.");
        }
    }

    /** A limpeza de uma participação já revogada só retira resíduos, nunca concede acesso ou gestão. */
    public static void requireRemovalAuthority(ProjectCommunity community, UUID actor, UUID target) {
        if (community.isOwner(target)) {
            throw new BusinessRuleException("O dono da comunidade não pode ser removido.");
        }
        if (!actor.equals(target) && (!community.isMember(actor) || !community.isAdmin(actor))) {
            throw new UnauthorizedAccessException("A remoção de terceiros exige OWNER ou ADMIN atuais.");
        }
        if (community.isMember(target) && !actor.equals(target) && community.getStatus() != CommunityStatus.ACTIVE) {
            throw new BusinessRuleException("A comunidade está arquivada.");
        }
    }
}
