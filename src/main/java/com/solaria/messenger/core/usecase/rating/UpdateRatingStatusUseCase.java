package com.solaria.messenger.core.usecase.rating;

import org.springframework.stereotype.Service;

import com.solaria.messenger.core.contract.request.RatingStatusUpdateRequestDTO;
import com.solaria.messenger.core.contract.response.RatingResponseDTO;
import com.solaria.messenger.core.helper.LookupHelper;
import com.solaria.messenger.core.model.Rating;
import com.solaria.messenger.core.repository.RatingRepository;
import com.solaria.messenger.core.security.rbac.RbacAuthorizationService;

import static com.solaria.messenger.core.mapper.RatingMapper.toResponse;

/**
 * Caso de uso que altera o status de uma avaliação do próprio avaliador.
 *
 * <p>
 * Só o avaliador que criou a avaliação pode mudar o status dela.
 * </p>
 *
 * <p>
 * <b>Depende de:</b>
 * </p>
 * <ul>
 *     <li>{@link RatingRepository} -> grava o novo status.</li>
 *     <li>{@link RbacAuthorizationService} -> confere se o user é o avaliador.</li>
 *     <li>{@link LookupHelper} -> carrega a avaliação.</li>
 * </ul>
 *
 * <p>
 * Principal operação disponibilizada:
 * </p>
 * <ul>
 *     <li>{@link #execute(String, RatingStatusUpdateRequestDTO)} -> altera o status.</li>
 * </ul>
 */
@Service
public class UpdateRatingStatusUseCase {

    private final RatingRepository ratingRepository;
    private final RbacAuthorizationService rbac;
    private final LookupHelper lookup;

    public UpdateRatingStatusUseCase(RatingRepository ratingRepository, RbacAuthorizationService rbac,
            LookupHelper lookup) {
        this.ratingRepository = ratingRepository;
        this.rbac = rbac;
        this.lookup = lookup;
    }

    /**
     * Altera o status da avaliação.
     *
     * @param id identificador da avaliação.
     * @param dto novo status.
     * @return avaliação com o status atualizado.
     * @throws ResourceNotFoundException caso a avaliação não exista.
     */
    public RatingResponseDTO execute(String id, RatingStatusUpdateRequestDTO dto) {
        Rating rating = lookup.requireRating(id);
        rbac.requireOwnResource(rating.getEvaluatorId());

        rating.setStatus(dto.getStatus());

        return toResponse(ratingRepository.save(rating));
    }
}
