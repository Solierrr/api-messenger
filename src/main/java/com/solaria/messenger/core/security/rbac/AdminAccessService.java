package com.solaria.messenger.core.security.rbac;

import java.util.Arrays;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.solaria.messenger.core.exception.UnauthorizedAccessException;

/**
 * Confere se o usuário autenticado é admin do histórico administrativo.
 *
 * <p>
 * Os admins vêm de {@code app.admin.subs}, uma lista de UUIDs separada por vírgula. Valores que não são UUID são
 * ignorados.
 * </p>
 *
 * <p>
 * <b>Depende de:</b>
 * </p>
 * <ul>
 *     <li>{@link RbacAuthorizationService} -> identifica o usuário autenticado.</li>
 * </ul>
 *
 * <p>
 * Principal operação disponibilizada:
 * </p>
 * <ul>
 *     <li>{@link #requireAdmin()} -> exige que o usuário autenticado seja admin.</li>
 * </ul>
 */
@Service
public class AdminAccessService {

    private static final Pattern UUID_PATTERN = Pattern.compile(
            "^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$");

    private final RbacAuthorizationService rbac;
    private final Set<UUID> admins;

    public AdminAccessService(RbacAuthorizationService rbac, @Value("${app.admin.subs:}") String subs) {
        this.rbac = rbac;
        this.admins = Arrays.stream(subs.split(","))
                .map(item -> item.trim().toLowerCase())
                .filter(item -> UUID_PATTERN.matcher(item).matches())
                .map(UUID::fromString)
                .collect(Collectors.toUnmodifiableSet());
    }

    /**
     * Exige que o usuário autenticado esteja na lista de admins.
     *
     * @throws UnauthorizedAccessException caso o usuário não seja admin.
     */
    public void requireAdmin() {
        if (!admins.contains(rbac.currentUserId())) {
            throw new UnauthorizedAccessException("Sem permissão para o histórico administrativo.");
        }
    }
}
