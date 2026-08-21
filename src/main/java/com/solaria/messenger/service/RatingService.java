package com.solaria.messenger.service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.solaria.messenger.dto.request.RatingRequestDTO;
import com.solaria.messenger.dto.request.RatingStatusUpdateRequestDTO;
import com.solaria.messenger.dto.request.RatingUpdateRequestDTO;
import com.solaria.messenger.dto.response.RatingResponseDTO;
import com.solaria.messenger.exception.ResourceNotFoundException;
import com.solaria.messenger.model.Rating;
import com.solaria.messenger.model.enums.RatingStatus;
import com.solaria.messenger.repository.RatingRepository;
import com.solaria.messenger.security.rbac.RbacAuthorizationService;

@Service
public class RatingService {

    private final RatingRepository ratingRepository;
    private final RbacAuthorizationService rbac;

    public RatingService(RatingRepository ratingRepository, RbacAuthorizationService rbac) {
        this.ratingRepository = ratingRepository;
        this.rbac = rbac;
    }

    public RatingResponseDTO create(RatingRequestDTO dto) {
        Rating rating = new Rating();
        rating.setEvaluatorId(rbac.currentUserId());
        rating.setEvaluatedId(dto.getEvaluatedId());
        rating.setTypeRate(dto.getTypeRate());
        rating.setComment(dto.getComment());
        rating.setRating(dto.getRating());
        rating.setStatus(RatingStatus.ACTIVE);
        rating.setTimestamp(Instant.now());

        return toResponse(ratingRepository.save(rating));
    }

    public RatingResponseDTO findById(String id) {
        Rating rating = findRatingOrThrow(id);
        return toResponse(rating);
    }


    public List<RatingResponseDTO> findByEvaluated(UUID evaluatedId) {
        return ratingRepository.findByEvaluatedId(evaluatedId).stream().map(this::toResponse).toList();
    }

    public List<RatingResponseDTO> findByEvaluator(UUID evaluatorId) {
        return ratingRepository.findByEvaluatorId(evaluatorId).stream().map(this::toResponse).toList();
    }

    public RatingResponseDTO update(String id, RatingUpdateRequestDTO dto) {
        Rating rating = findRatingOrThrow(id);
        rbac.requireOwnResource(rating.getEvaluatorId());

        if (dto.getComment() != null) {
            rating.setComment(dto.getComment());
        }
        if (dto.getRating() != null) {
            rating.setRating(dto.getRating());
        }

        return toResponse(ratingRepository.save(rating));
    }

    public RatingResponseDTO updateStatus(String id, RatingStatusUpdateRequestDTO dto) {
        Rating rating = findRatingOrThrow(id);
        rbac.requireOwnResource(rating.getEvaluatorId());

        rating.setStatus(dto.getStatus());

        return toResponse(ratingRepository.save(rating));
    }

    private Rating findRatingOrThrow(String id) {
        return ratingRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Avaliação não encontrada com ID: " + id));
    }

    private RatingResponseDTO toResponse(Rating rating) {
        return RatingResponseDTO.builder()
                .id(rating.getId())
                .evaluatorId(rating.getEvaluatorId())
                .evaluatedId(rating.getEvaluatedId())
                .typeRate(rating.getTypeRate())
                .comment(rating.getComment())
                .rating(rating.getRating())
                .status(rating.getStatus())
                .timestamp(rating.getTimestamp())
                .build();
    }
}
