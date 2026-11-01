package com.solaria.messenger.rest.controller;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.solaria.messenger.core.contract.response.LangSmithSummaryDTO;
import com.solaria.messenger.core.security.rbac.AdminAccessService;
import com.solaria.messenger.core.usecase.admin.GetLangSmithSummaryUseCase;

import io.swagger.v3.oas.annotations.Hidden;

/**
 * Controller REST do resumo do LangSmith no painel administrativo, exposto em
 * {@code /messaging/admin/langsmith}.
 *
 * <p>
 * Fica fora do OpenAPI ({@code @Hidden}). O endpoint exige um admin de {@code app.admin.subs}, conferido pelo
 * {@link AdminAccessService}, e delega o resumo ao {@link GetLangSmithSummaryUseCase}.
 * </p>
 *
 * <p>
 * Principal operação disponibilizada:
 * </p>
 * <ul>
 *     <li>{@link #summary(LocalDate, LocalDate)} -> devolve o resumo do LangSmith do período.</li>
 * </ul>
 */
@Hidden
@RestController
@RequestMapping("/messaging/admin/langsmith")
public class AdminLangSmithController {

    private final AdminAccessService adminAccess;
    private final GetLangSmithSummaryUseCase getSummary;

    public AdminLangSmithController(AdminAccessService adminAccess, GetLangSmithSummaryUseCase getSummary) {
        this.adminAccess = adminAccess;
        this.getSummary = getSummary;
    }

    /** Devolve o resumo do LangSmith do período */
    @GetMapping("/summary")
    public ResponseEntity<LangSmithSummaryDTO> summary(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        adminAccess.requireAdmin();
        return ResponseEntity.ok(getSummary.execute(from, to));
    }
}
