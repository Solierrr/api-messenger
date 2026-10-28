package com.solaria.messenger.core.usecase.rating;

import org.springframework.stereotype.Service;

import com.solaria.messenger.core.contract.request.RatingUpdateRequestDTO;
import com.solaria.messenger.core.contract.response.RatingResponseDTO;
import com.solaria.messenger.core.helper.LookupHelper;
import com.solaria.messenger.core.model.Rating;
import com.solaria.messenger.core.repository.RatingRepository;
import com.solaria.messenger.core.security.rbac.RbacAuthorizationService;

import static com.solaria.messenger.core.mapper.RatingMapper.toResponse;

/**
 * Caso de uso que edita o comentário ou a nota de uma avaliação do próprio avaliador.
 *
 * <p>
 * Só os campos enviados são alterados. Campo nulo no pedido fica como está.
 * </p>
 *
 * <p>
 * <b>Depende de:</b>
 * </p>
 * <ul>
 *     <li>{@link RatingRepository} -> grava a avaliação.</li>
 *     <li>{@link RbacAuthorizationService} -> confere se o user é o avaliador.</li>
 *     <li>{@link LookupHelper} -> carrega a avaliação.</li>
 * </ul>
 *
 * <p>
 * Principal operação disponibilizada:
 * </p>
 * <ul>
 *     <li>{@link #execute(String, RatingUpdateRequestDTO)} -> edita comentário e nota.</li>
 * </ul>
 */
@Service
public class UpdateRatingUseCase {

    private final RatingRepository ratingRepository;
    private final RbacAuthorizationService rbac;
    private final LookupHelper lookup;

    public UpdateRatingUseCase(RatingRepository ratingRepository, RbacAuthorizationService rbac, LookupHelper lookup) {
        this.ratingRepository = ratingRepository;
        this.rbac = rbac;
        this.lookup = lookup;
    }

    /**
     * Edita o comentário e a nota da avaliação.
     *
     * @param id identificador da avaliação.
     * @param dto comentário e nota novos. Campo nulo não é alterado.
     * @return avaliação com os campos atualizados.
     * @throws ResourceNotFoundException caso a avaliação não exista.
     */
    public RatingResponseDTO execute(String id, RatingUpdateRequestDTO dto) {
        Rating rating = lookup.requireRating(id);
        rbac.requireOwnResource(rating.getEvaluatorId());

        if (dto.getComment() != null) {
            rating.setComment(dto.getComment());
        }
        if (dto.getRating() != null) {
            rating.setRating(dto.getRating());
        }

        return toResponse(ratingRepository.save(rating));
    }
}
