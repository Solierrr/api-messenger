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
import com.solaria.messenger.core.service.LangSmithSummaryService;

import io.swagger.v3.oas.annotations.Hidden;

@Hidden
@RestController
@RequestMapping("/messaging/admin/langsmith")
public class AdminLangSmithController {

    private final AdminAccessService adminAccess;
    private final LangSmithSummaryService summaryService;

    public AdminLangSmithController(AdminAccessService adminAccess, LangSmithSummaryService summaryService) {
        this.adminAccess = adminAccess;
        this.summaryService = summaryService;
    }

    @GetMapping("/summary")
    public ResponseEntity<LangSmithSummaryDTO> summary(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        adminAccess.requireAdmin();
        return ResponseEntity.ok(summaryService.summary(from, to));
    }
}
