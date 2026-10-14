package com.solaria.messenger.controller;

import java.net.URI;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.solaria.messenger.dto.request.NpsRequestDTO;
import com.solaria.messenger.dto.response.NpsResponseDTO;
import com.solaria.messenger.openapi.NpsOpenApi;
import com.solaria.messenger.service.NpsService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/messaging/nps")
public class NpsController implements NpsOpenApi {

    private final NpsService npsService;

    public NpsController(NpsService npsService) {
        this.npsService = npsService;
    }

    @Override
    @PostMapping
    public ResponseEntity<NpsResponseDTO> create(@Valid @RequestBody NpsRequestDTO dto) {
        NpsResponseDTO response = npsService.create(dto);
        return ResponseEntity.created(URI.create("/messaging/nps/" + response.getId())).body(response);
    }
}
