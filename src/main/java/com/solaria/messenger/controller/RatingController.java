package com.solaria.messenger.controller;

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

import com.solaria.messenger.dto.request.RatingRequestDTO;
import com.solaria.messenger.dto.request.RatingStatusUpdateRequestDTO;
import com.solaria.messenger.dto.request.RatingUpdateRequestDTO;
import com.solaria.messenger.dto.response.RatingResponseDTO;
import com.solaria.messenger.openapi.RatingOpenApi;
import com.solaria.messenger.service.RatingService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/v1/ratings")
public class RatingController implements RatingOpenApi {

    private final RatingService ratingService;

    public RatingController(RatingService ratingService) {
        this.ratingService = ratingService;
    }

    @Override
    @PostMapping
    public ResponseEntity<RatingResponseDTO> create(@Valid @RequestBody RatingRequestDTO dto) {
        RatingResponseDTO response = ratingService.create(dto);
        return ResponseEntity.created(URI.create("/api/v1/ratings/" + response.getId())).body(response);
    }

    @Override
    @GetMapping("/{id}")
    public ResponseEntity<RatingResponseDTO> findById(@PathVariable String id) {
        return ResponseEntity.ok(ratingService.findById(id));
    }

    @Override
    @GetMapping("/evaluated/{evaluatedId}")
    public ResponseEntity<List<RatingResponseDTO>> findByEvaluated(@PathVariable UUID evaluatedId) {
        return ResponseEntity.ok(ratingService.findByEvaluated(evaluatedId));
    }

    @Override
    @GetMapping("/evaluator/{evaluatorId}")
    public ResponseEntity<List<RatingResponseDTO>> findByEvaluator(@PathVariable UUID evaluatorId) {
        return ResponseEntity.ok(ratingService.findByEvaluator(evaluatorId));
    }

    @Override
    @PatchMapping("/{id}")
    public ResponseEntity<RatingResponseDTO> update(@PathVariable String id,
                                                      @Valid @RequestBody RatingUpdateRequestDTO dto) {
        return ResponseEntity.ok(ratingService.update(id, dto));
    }

    @Override
    @PatchMapping("/{id}/status")
    public ResponseEntity<RatingResponseDTO> updateStatus(@PathVariable String id,
                                                            @Valid @RequestBody RatingStatusUpdateRequestDTO dto) {
        return ResponseEntity.ok(ratingService.updateStatus(id, dto));
    }
}
