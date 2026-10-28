package com.solaria.messenger.core.usecase.rating;

import org.springframework.stereotype.Service;

import com.solaria.messenger.core.contract.response.RatingResponseDTO;
import com.solaria.messenger.core.helper.LookupHelper;

import static com.solaria.messenger.core.mapper.RatingMapper.toResponse;

/**
 * Caso de uso que busca uma avaliação pelo identificador.
 *
 * <p>
 * A consulta não confere autoria, porque o caso de uso não recebe o user atual.
 * </p>
 *
 * <p>
 * <b>Depende de:</b>
 * </p>
 * <ul>
 *     <li>{@link LookupHelper} -> carrega a avaliação.</li>
 * </ul>
 *
 * <p>
 * Principal operação disponibilizada:
 * </p>
 * <ul>
 *     <li>{@link #execute(String)} -> busca a avaliação pelo identificador.</li>
 * </ul>
 */
@Service
public class GetRatingUseCase {

    private final LookupHelper lookup;

    public GetRatingUseCase(LookupHelper lookup) {
        this.lookup = lookup;
    }

    /**
     * Busca uma avaliação pelo identificador.
     *
     * @param id identificador da avaliação.
     * @return avaliação encontrada.
     * @throws ResourceNotFoundException caso a avaliação não exista.
     */
    public RatingResponseDTO execute(String id) {
        return toResponse(lookup.requireRating(id));
    }
}
