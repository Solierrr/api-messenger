package com.solaria.messenger.rest.controller;

import java.net.URI;
import java.util.List;
import java.util.UUID;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.solaria.messenger.core.contract.request.RatingRequestDTO;
import com.solaria.messenger.core.contract.request.RatingStatusUpdateRequestDTO;
import com.solaria.messenger.core.contract.request.RatingUpdateRequestDTO;
import com.solaria.messenger.core.contract.response.RatingResponseDTO;
import com.solaria.messenger.rest.openapi.RatingOpenApi;
import com.solaria.messenger.core.service.RatingService;

import jakarta.validation.Valid;

/**
 * Controller REST das avaliações, exposto em {@code /messaging/ratings}.
 *
 * <p>Atua na camada de entrada HTTP: valida o corpo ({@code @Valid}) e delega a regra de negócio a
 * {@link RatingService}. O contrato da API é declarado em {@link RatingOpenApi}.</p>
 *
 * <p>Principais operações disponibilizadas:</p>
 * <ul>
 *     <li>{@link #create(RatingRequestDTO)} -> cria uma avaliação do usuário autenticado (201).</li>
 *     <li>{@link #findById(String)} -> busca uma avaliação pelo identificador.</li>
 *     <li>{@link #findByEvaluated(UUID)} -> lista as avaliações recebidas por um avaliado.</li>
 *     <li>{@link #findByEvaluator(UUID)} -> lista as avaliações feitas por um avaliador.</li>
 *     <li>{@link #update(String, RatingUpdateRequestDTO)} -> edita comentário e/ou nota de uma avaliação própria.</li>
 *     <li>{@link #updateStatus(String, RatingStatusUpdateRequestDTO)} -> altera o status de uma avaliação própria.</li>
 * </ul>
 */
@RestController
@RequestMapping("/messaging/ratings")
public class RatingController implements RatingOpenApi {

    private final RatingService ratingService;

    public RatingController(RatingService ratingService) {
        this.ratingService = ratingService;
    }

    /** Cria uma avaliação feita pelo usuário autenticado e devolve 201. */
    @Override
    @PostMapping
    public ResponseEntity<RatingResponseDTO> create(@Valid @RequestBody RatingRequestDTO dto) {
        RatingResponseDTO response = ratingService.create(dto);
        return ResponseEntity.created(URI.create("/messaging/ratings/" + response.getId())).body(response);
    }

    /** Busca uma avaliação pelo ID. */
    @Override
    @GetMapping("/{id}")
    public ResponseEntity<RatingResponseDTO> findById(@PathVariable String id) {
        return ResponseEntity.ok(ratingService.findById(id));
    }

    /** Lista as avaliações recebidas por um avaliado. */
    @Override
    @GetMapping("/evaluated/{evaluatedId}")
    public ResponseEntity<List<RatingResponseDTO>> findByEvaluated(@PathVariable UUID evaluatedId) {
        return ResponseEntity.ok(ratingService.findByEvaluated(evaluatedId));
    }

    /** Lista as avaliações feitas por um avaliador. */
    @Override
    @GetMapping("/evaluator/{evaluatorId}")
    public ResponseEntity<List<RatingResponseDTO>> findByEvaluator(@PathVariable UUID evaluatorId) {
        return ResponseEntity.ok(ratingService.findByEvaluator(evaluatorId));
    }

    /** Edita comentário e/ou nota de uma avaliação própria. */
    @Override
    @PatchMapping("/{id}")
    public ResponseEntity<RatingResponseDTO> update(@PathVariable String id,
                                                      @Valid @RequestBody RatingUpdateRequestDTO dto) {
        return ResponseEntity.ok(ratingService.update(id, dto));
    }

    /** Altera o status de uma avaliação própria. */
    @Override
    @PatchMapping("/{id}/status")
    public ResponseEntity<RatingResponseDTO> updateStatus(@PathVariable String id,
                                                            @Valid @RequestBody RatingStatusUpdateRequestDTO dto) {
        return ResponseEntity.ok(ratingService.updateStatus(id, dto));
    }
}
