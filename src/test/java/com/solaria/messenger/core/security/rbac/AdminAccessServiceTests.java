package com.solaria.messenger.core.security.rbac;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.solaria.messenger.core.exception.UnauthorizedAccessException;

@ExtendWith(MockitoExtension.class)
class AdminAccessServiceTests {

    private static final UUID ADMIN = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID OTHER = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");

    @Mock
    private RbacAuthorizationService rbac;

    @Test
    void liberaSoQuemEstaNaListaIgnorandoCaixaEEspacos() {
        AdminAccessService access = new AdminAccessService(rbac, " AAAAAAAA-AAAA-AAAA-AAAA-AAAAAAAAAAAA , lixo ");
        given(rbac.currentUserId()).willReturn(ADMIN);

        assertThatCode(access::requireAdmin).doesNotThrowAnyException();
    }

    @Test
    void negaQuemNaoEstaNaLista() {
        AdminAccessService access = new AdminAccessService(rbac, ADMIN.toString());
        given(rbac.currentUserId()).willReturn(OTHER);

        assertThatThrownBy(access::requireAdmin).isInstanceOf(UnauthorizedAccessException.class);
    }

    @Test
    void semConfiguracaoNegaTodos() {
        AdminAccessService access = new AdminAccessService(rbac, "");
        given(rbac.currentUserId()).willReturn(ADMIN);

        assertThatThrownBy(access::requireAdmin).isInstanceOf(UnauthorizedAccessException.class);
    }
}
