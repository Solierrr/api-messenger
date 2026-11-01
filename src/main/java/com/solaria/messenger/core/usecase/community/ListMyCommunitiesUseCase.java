package com.solaria.messenger.core.usecase.community;

import java.util.List;

import org.springframework.stereotype.Service;

import com.solaria.messenger.core.contract.response.CommunityResponseDTO;
import com.solaria.messenger.core.mapper.CommunityMapper;
import com.solaria.messenger.core.repository.CommunityRepository;
import com.solaria.messenger.core.security.rbac.RbacAuthorizationService;

/**
 * Caso de uso que lista as comunidades do user atual.
 *
 * <p>
 * Traz as comunidades em que o user aparece como membro.
 * </p>
 *
 * <p>
 * <b>Depende de:</b>
 * </p>
 * <ul>
 *     <li>{@link CommunityRepository} -> busca as comunidades do user.</li>
 *     <li>{@link RbacAuthorizationService} -> identifica o user atual.</li>
 * </ul>
 *
 * <p>
 * Principal operação disponibilizada:
 * </p>
 * <ul>
 *     <li>{@link #execute()} -> lista as comunidades do user.</li>
 * </ul>
 */
@Service
public class ListMyCommunitiesUseCase {

    private final CommunityRepository communityRepository;
    private final RbacAuthorizationService rbac;

    public ListMyCommunitiesUseCase(CommunityRepository communityRepository, RbacAuthorizationService rbac) {
        this.communityRepository = communityRepository;
        this.rbac = rbac;
    }

    /**
     * Lista as comunidades do user autenticado.
     *
     * @return comunidades em que o user é membro.
     */
    public List<CommunityResponseDTO> execute() {
        return communityRepository.findAllByMemberId(rbac.currentUserId()).stream()
                .map(CommunityMapper::toResponse)
                .toList();
    }
}
