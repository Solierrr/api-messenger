package com.solaria.messenger.rest.controller;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.solaria.messenger.core.contract.response.AdminConversationDetailDTO;
import com.solaria.messenger.core.contract.response.AdminConversationPageDTO;
import com.solaria.messenger.core.security.rbac.AdminAccessService;
import com.solaria.messenger.core.usecase.admin.GetAdminConversationUseCase;
import com.solaria.messenger.core.usecase.admin.ListAdminConversationsUseCase;

import io.swagger.v3.oas.annotations.Hidden;

/**
 * Controller REST do histórico administrativo das conversas com o chatbot, exposto em
 * {@code /messaging/admin}.
 *
 * <p>
 * Fica fora do OpenAPI ({@code @Hidden}). Cada endpoint exige um admin de {@code app.admin.subs}, conferido pelo
 * {@link AdminAccessService}, e delega a consulta aos use cases do histórico.
 * </p>
 *
 * <p>
 * Principais operações disponibilizadas:
 * </p>
 * <ul>
 *     <li>{@link #list(LocalDate, LocalDate, String, String, String, String, Integer)} -> lista as conversas do
 *         período com filtros.</li>
 *     <li>{@link #detail(String)} -> devolve uma conversa com as mensagens e as etapas do LLM.</li>
 * </ul>
 */
@Hidden
@RestController
@RequestMapping("/messaging/admin")
public class AdminHistoryController {

    private final AdminAccessService adminAccess;
    private final ListAdminConversationsUseCase listConversations;
    private final GetAdminConversationUseCase getConversation;

    public AdminHistoryController(AdminAccessService adminAccess, ListAdminConversationsUseCase listConversations,
            GetAdminConversationUseCase getConversation) {
        this.adminAccess = adminAccess;
        this.listConversations = listConversations;
        this.getConversation = getConversation;
    }

    /** Lista as conversas com o chatbot do período, com filtros opcionais */
    @GetMapping("/conversations")
    public ResponseEntity<AdminConversationPageDTO> list(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(required = false) String assistant,
            @RequestParam(required = false) String sessionId,
            @RequestParam(required = false) String userId,
            @RequestParam(required = false) String text,
            @RequestParam(required = false) Integer limit) {
        adminAccess.requireAdmin();
        return ResponseEntity.ok(listConversations.execute(from, to, assistant, sessionId, userId, text, limit));
    }

    /** Devolve o detalhe de uma conversa com o chatbot */
    @GetMapping("/conversations/{id}")
    public ResponseEntity<AdminConversationDetailDTO> detail(@PathVariable String id) {
        adminAccess.requireAdmin();
        return ResponseEntity.ok(getConversation.execute(id));
    }
}
