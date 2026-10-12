package com.solaria.messenger.core.service;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.solaria.messenger.core.contract.request.RatingRequestDTO;
import com.solaria.messenger.core.contract.request.RatingStatusUpdateRequestDTO;
import com.solaria.messenger.core.contract.request.RatingUpdateRequestDTO;
import com.solaria.messenger.core.contract.response.RatingResponseDTO;
import com.solaria.messenger.core.exception.BusinessRuleException;
import com.solaria.messenger.core.exception.ResourceNotFoundException;
import com.solaria.messenger.core.model.Rating;
import com.solaria.messenger.core.model.enums.RatingStatus;
import com.solaria.messenger.core.repository.RatingRepository;
import com.solaria.messenger.core.security.rbac.RbacAuthorizationService;
import com.solaria.messenger.core.mapper.RatingMapper;

import static com.solaria.messenger.core.mapper.RatingMapper.toResponse;

/**
 * Serviço de regras de negócio das avaliações ({@link Rating}).
 *
 * <p>Impede a autoavaliação e restringe a edição e a alteração de status ao próprio avaliador,
 * conforme a checagem de RBAC do usuário autenticado.</p>
 *
 * <p><b>Depende de:</b></p>
 * <ul>
 *     <li>{@link RatingRepository} - persistência das avaliações.</li>
 *     <li>{@link RbacAuthorizationService} - identificação do usuário e checagem de propriedade.</li>
 * </ul>
 *
 * <p>Principais operações disponibilizadas:</p>
 * <ul>
 *     <li>{@link #create(RatingRequestDTO)} - cria uma avaliação ativa.</li>
 *     <li>{@link #findById(String)} - busca uma avaliação pelo identificador.</li>
 *     <li>{@link #findByEvaluated(UUID)} - lista as avaliações recebidas por um avaliado.</li>
 *     <li>{@link #findByEvaluator(UUID)} - lista as avaliações feitas por um avaliador.</li>
 *     <li>{@link #update(String, RatingUpdateRequestDTO)} - edita comentário e/ou nota.</li>
 *     <li>{@link #updateStatus(String, RatingStatusUpdateRequestDTO)} - altera o status da avaliação.</li>
 * </ul>
 */
@Service
public class RatingService {

    private final RatingRepository ratingRepository;
    private final RbacAuthorizationService rbac;

    public RatingService(RatingRepository ratingRepository, RbacAuthorizationService rbac) {
        this.ratingRepository = ratingRepository;
        this.rbac = rbac;
    }

    /** Cria uma avaliação ativa; ninguém pode avaliar a si mesmo. */
    public RatingResponseDTO create(RatingRequestDTO dto) {
        UUID evaluatorId = rbac.currentUserId();
        if (evaluatorId.equals(dto.getEvaluatedId())) {
            throw new BusinessRuleException("Não é permitido avaliar a si mesmo.");
        }

        Rating rating = new Rating();
        rating.setEvaluatorId(evaluatorId);
        rating.setEvaluatedId(dto.getEvaluatedId());
        rating.setTypeRate(dto.getTypeRate());
        rating.setComment(dto.getComment());
        rating.setRating(dto.getRating());
        rating.setStatus(RatingStatus.ACTIVE);
        rating.setTimestamp(Instant.now());

        return toResponse(ratingRepository.save(rating));
    }

    /** Busca uma avaliação pelo ID. */
    public RatingResponseDTO findById(String id) {
        Rating rating = findRatingOrThrow(id);
        return toResponse(rating);
    }


    /** Lista as avaliações recebidas por um avaliado. */
    public List<RatingResponseDTO> findByEvaluated(UUID evaluatedId) {
        return ratingRepository.findByEvaluatedId(evaluatedId).stream().map(RatingMapper::toResponse).toList();
    }

    /** Lista as avaliações feitas por um avaliador. */
    public List<RatingResponseDTO> findByEvaluator(UUID evaluatorId) {
        return ratingRepository.findByEvaluatorId(evaluatorId).stream().map(RatingMapper::toResponse).toList();
    }

    /** Edita comentário e/ou nota de uma avaliação do próprio avaliador. */
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

    /** Altera o status de uma avaliação do próprio avaliador. */
    public RatingResponseDTO updateStatus(String id, RatingStatusUpdateRequestDTO dto) {
        Rating rating = findRatingOrThrow(id);
        rbac.requireOwnResource(rating.getEvaluatorId());

        rating.setStatus(dto.getStatus());

        return toResponse(ratingRepository.save(rating));
    }

    /** Carrega a avaliação ou lança recurso inexistente. */
    private Rating findRatingOrThrow(String id) {
        return ratingRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Avaliação não encontrada com ID: " + id));
    }
}
