package com.solaria.messenger.core.security.rbac;

import java.util.Arrays;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import com.solaria.messenger.core.exception.UnauthorizedAccessException;

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

    public void requireAdmin() {
        if (!admins.contains(rbac.currentUserId())) {
            throw new UnauthorizedAccessException("Sem permissão para o histórico administrativo.");
        }
    }
}
