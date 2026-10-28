package com.solaria.messenger.core.usecase.community;

import org.springframework.stereotype.Service;

import com.solaria.messenger.core.contract.response.CommunityResponseDTO;
import com.solaria.messenger.core.helper.LookupHelper;
import com.solaria.messenger.core.model.ProjectCommunity;
import com.solaria.messenger.core.security.rbac.RbacAuthorizationService;

import static com.solaria.messenger.core.helper.PureRulesHelper.requireMember;
import static com.solaria.messenger.core.mapper.CommunityMapper.toResponse;

/**
 * Caso de uso que busca uma comunidade pelo identificador.
 *
 * <p>
 * Devolve a comunidade só para quem é membro dela.
 * </p>
 *
 * <p>
 * <b>Depende de:</b>
 * </p>
 * <ul>
 *     <li>{@link LookupHelper} -> carrega a comunidade.</li>
 *     <li>{@link RbacAuthorizationService} -> identifica o user atual.</li>
 * </ul>
 *
 * <p>
 * Principal operação disponibilizada:
 * </p>
 * <ul>
 *     <li>{@link #execute(String)} -> busca a comunidade pelo identificador.</li>
 * </ul>
 */
@Service
public class GetCommunityUseCase {

    private final LookupHelper lookup;
    private final RbacAuthorizationService rbac;

    public GetCommunityUseCase(LookupHelper lookup, RbacAuthorizationService rbac) {
        this.lookup = lookup;
        this.rbac = rbac;
    }

    /**
     * Busca uma comunidade da qual o user é membro.
     *
     * @param id identificador da comunidade.
     * @return comunidade encontrada.
     * @throws ResourceNotFoundException caso a comunidade não exista.
     * @throws UnauthorizedAccessException caso o user atual não seja membro.
     */
    public CommunityResponseDTO execute(String id) {
        ProjectCommunity community = lookup.requireCommunity(id);
        requireMember(community, rbac.currentUserId());
        return toResponse(community);
    }
}
