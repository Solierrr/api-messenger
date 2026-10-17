package com.solaria.messenger.rest.controller;

import java.net.URI;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.solaria.messenger.core.contract.request.NpsRequestDTO;
import com.solaria.messenger.core.contract.response.NpsResponseDTO;
import com.solaria.messenger.rest.openapi.NpsOpenApi;
import com.solaria.messenger.core.service.NpsService;

import jakarta.validation.Valid;

/**
 * Controller REST do NPS (Net Promoter Score), exposto em {@code /messaging/nps}.
 *
 * <p>Recebe a resposta, valida o corpo ({@code @Valid}) e delega o registro a {@link NpsService}. O
 * contrato da API é declarado em {@link NpsOpenApi}.</p>
 *
 * <p>Principais operações disponibilizadas:</p>
 * <ul>
 *     <li>{@link #create(NpsRequestDTO)} -> registra o NPS de uma conversa com o chatbot (201).</li>
 * </ul>
 */
@RestController
@RequestMapping("/messaging/nps")
public class NpsController implements NpsOpenApi {

    private final NpsService npsService;

    public NpsController(NpsService npsService) {
        this.npsService = npsService;
    }

    /** Registra o NPS de uma conversa com o chatbot e devolve 201. */
    @Override
    @PostMapping
    public ResponseEntity<NpsResponseDTO> create(@Valid @RequestBody NpsRequestDTO dto) {
        NpsResponseDTO response = npsService.create(dto);
        return ResponseEntity.created(URI.create("/messaging/nps/" + response.getId())).body(response);
    }
}
