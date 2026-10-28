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
import com.solaria.messenger.core.usecase.rating.CreateRatingUseCase;
import com.solaria.messenger.core.usecase.rating.GetRatingUseCase;
import com.solaria.messenger.core.usecase.rating.ListRatingsByEvaluatedUseCase;
import com.solaria.messenger.core.usecase.rating.ListRatingsByEvaluatorUseCase;
import com.solaria.messenger.core.usecase.rating.UpdateRatingStatusUseCase;
import com.solaria.messenger.core.usecase.rating.UpdateRatingUseCase;

import jakarta.validation.Valid;

/**
 * Controller REST das avaliações, exposto em {@code /messaging/ratings}.
 *
 * <p>
 * Atua na camada de entrada HTTP: valida o corpo ({@code @Valid}) e delega a regra de negócio aos
 * use cases de avaliação. O contrato da API é declarado em {@link RatingOpenApi}.
 * </p>
 *
 * <p>
 * Principais operações disponibilizadas:
 * </p>
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

    private final CreateRatingUseCase createRating;
    private final GetRatingUseCase getRating;
    private final ListRatingsByEvaluatedUseCase listByEvaluated;
    private final ListRatingsByEvaluatorUseCase listByEvaluator;
    private final UpdateRatingUseCase updateRating;
    private final UpdateRatingStatusUseCase updateRatingStatus;

    public RatingController(CreateRatingUseCase createRating, GetRatingUseCase getRating,
            ListRatingsByEvaluatedUseCase listByEvaluated, ListRatingsByEvaluatorUseCase listByEvaluator,
            UpdateRatingUseCase updateRating, UpdateRatingStatusUseCase updateRatingStatus) {
        this.createRating = createRating;
        this.getRating = getRating;
        this.listByEvaluated = listByEvaluated;
        this.listByEvaluator = listByEvaluator;
        this.updateRating = updateRating;
        this.updateRatingStatus = updateRatingStatus;
    }

    /** Cria uma avaliação feita pelo usuário autenticado e devolve 201. */
    @Override
    @PostMapping
    public ResponseEntity<RatingResponseDTO> create(@Valid @RequestBody RatingRequestDTO dto) {
        RatingResponseDTO response = createRating.execute(dto);
        return ResponseEntity.created(URI.create("/messaging/ratings/" + response.getId())).body(response);
    }

    /** Busca uma avaliação pelo ID. */
    @Override
    @GetMapping("/{id}")
    public ResponseEntity<RatingResponseDTO> findById(@PathVariable String id) {
        return ResponseEntity.ok(getRating.execute(id));
    }

    /** Lista as avaliações recebidas por um avaliado. */
    @Override
    @GetMapping("/evaluated/{evaluatedId}")
    public ResponseEntity<List<RatingResponseDTO>> findByEvaluated(@PathVariable UUID evaluatedId) {
        return ResponseEntity.ok(listByEvaluated.execute(evaluatedId));
    }

    /** Lista as avaliações feitas por um avaliador. */
    @Override
    @GetMapping("/evaluator/{evaluatorId}")
    public ResponseEntity<List<RatingResponseDTO>> findByEvaluator(@PathVariable UUID evaluatorId) {
        return ResponseEntity.ok(listByEvaluator.execute(evaluatorId));
    }

    /** Edita comentário e/ou nota de uma avaliação própria. */
    @Override
    @PatchMapping("/{id}")
    public ResponseEntity<RatingResponseDTO> update(@PathVariable String id,
                                                      @Valid @RequestBody RatingUpdateRequestDTO dto) {
        return ResponseEntity.ok(updateRating.execute(id, dto));
    }

    /** Altera o status de uma avaliação própria. */
    @Override
    @PatchMapping("/{id}/status")
    public ResponseEntity<RatingResponseDTO> updateStatus(@PathVariable String id,
                                                            @Valid @RequestBody RatingStatusUpdateRequestDTO dto) {
        return ResponseEntity.ok(updateRatingStatus.execute(id, dto));
    }
}
