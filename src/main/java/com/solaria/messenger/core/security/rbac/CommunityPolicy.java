package com.solaria.messenger.core.security.rbac;

import java.util.UUID;

import com.solaria.messenger.core.exception.UnauthorizedAccessException;
import com.solaria.messenger.core.model.ProjectCommunity;

/** Autorização por papel ({@code OWNER/ADMIN/MEMBER/VIEWER}) na comunidade, compartilhada pelos serviços e pela cascata. */
public final class CommunityPolicy {
    private CommunityPolicy() { }

    /** Exige que o ator seja membro da comunidade. */
    public static void requireMember(ProjectCommunity community, UUID actor) {
        if (!community.isMember(actor)) {
            throw new UnauthorizedAccessException("O usuário autenticado não participa desta comunidade.");
        }
    }

    /** Exige um membro que possa escrever; o VIEWER só consulta. */
    public static void requireWriter(ProjectCommunity community, UUID actor) {
        requireMember(community, actor);
        if (!community.canWrite(actor)) {
            throw new UnauthorizedAccessException("Projeto disponível apenas para consulta.");
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
}
