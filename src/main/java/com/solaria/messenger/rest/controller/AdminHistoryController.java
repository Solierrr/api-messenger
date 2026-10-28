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
import com.solaria.messenger.core.service.AdminHistoryService;

import io.swagger.v3.oas.annotations.Hidden;

@Hidden
@RestController
@RequestMapping("/messaging/admin")
public class AdminHistoryController {

    private final AdminAccessService adminAccess;
    private final AdminHistoryService adminHistoryService;

    public AdminHistoryController(AdminAccessService adminAccess, AdminHistoryService adminHistoryService) {
        this.adminAccess = adminAccess;
        this.adminHistoryService = adminHistoryService;
    }

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
        return ResponseEntity.ok(adminHistoryService.list(from, to, assistant, sessionId, userId, text, limit));
    }

    @GetMapping("/conversations/{id}")
    public ResponseEntity<AdminConversationDetailDTO> detail(@PathVariable String id) {
        adminAccess.requireAdmin();
        return ResponseEntity.ok(adminHistoryService.detail(id));
    }
}
