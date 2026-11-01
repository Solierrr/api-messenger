package com.solaria.messenger.core.usecase.community;

import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.solaria.messenger.core.contract.response.SubgroupMetadataResponseDTO;
import com.solaria.messenger.core.helper.CommunityHelper;
import com.solaria.messenger.core.helper.LookupHelper;
import com.solaria.messenger.core.mapper.ConversationMapper;
import com.solaria.messenger.core.model.ProjectCommunity;
import com.solaria.messenger.core.model.enums.ConversationType;
import com.solaria.messenger.core.repository.ConversationRepository;
import com.solaria.messenger.core.security.rbac.RbacAuthorizationService;

import static com.solaria.messenger.core.helper.PureRulesHelper.requireMember;

/**
 * Caso de uso que lista os subgrupos de uma comunidade.
 *
 * <p>
 * {@code OWNER} e {@code ADMIN} veem todos os subgrupos. Um membro comum vê só os grupos de que participa. As duas
 * listas saem da interação mais recente para a mais antiga.
 * </p>
 *
 * <p>
 * <b>Depende de:</b>
 * </p>
 * <ul>
 *     <li>{@link LookupHelper} -> carrega a comunidade.</li>
 *     <li>{@link RbacAuthorizationService} -> identifica o user atual.</li>
 *     <li>{@link ConversationRepository} -> lista os grupos de que o user participa.</li>
 *     <li>{@link CommunityHelper} -> lista todos os subgrupos para administradores.</li>
 * </ul>
 *
 * <p>
 * Principal operação disponibilizada:
 * </p>
 * <ul>
 *     <li>{@link #execute(String)} -> lista os subgrupos visíveis para o user.</li>
 * </ul>
 */
@Service
public class ListCommunitySubgroupsUseCase {

    private final LookupHelper lookup;
    private final RbacAuthorizationService rbac;
    private final ConversationRepository conversationRepository;
    private final CommunityHelper communities;

    public ListCommunitySubgroupsUseCase(LookupHelper lookup, RbacAuthorizationService rbac,
            ConversationRepository conversations, CommunityHelper communities) {
        this.lookup = lookup;
        this.rbac = rbac;
        this.conversationRepository = conversations;
        this.communities = communities;
    }

    /**
     * Lista os subgrupos da comunidade visíveis para o user atual.
     *
     * @param id identificador da comunidade.
     * @return subgrupos em formato de listagem.
     * @throws UnauthorizedAccessException caso o user atual não seja membro da comunidade.
     */
    public List<SubgroupMetadataResponseDTO> execute(String id) {
        ProjectCommunity community = lookup.requireCommunity(id);
        UUID actor = rbac.currentUserId();
        requireMember(community, actor);
        return community.isAdmin(actor) ? communities.subgroupMetadata(community.getId())
                : conversationRepository.findByCommunityIdAndConversationTypeAndParticipantIdsContainingOrderByLastInteractionAtDesc(
                        community.getId(), ConversationType.GROUP, actor).stream().map(ConversationMapper::toSubgroupMetadata).toList();
    }
}
