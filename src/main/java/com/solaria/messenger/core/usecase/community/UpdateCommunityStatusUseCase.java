package com.solaria.messenger.core.usecase.community;

import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.solaria.messenger.core.contract.request.CommunityUpdateRequestDTO;
import com.solaria.messenger.core.contract.response.CommunityResponseDTO;
import com.solaria.messenger.core.contract.response.SubgroupMetadataResponseDTO;
import com.solaria.messenger.core.exception.InvalidFieldException;
import com.solaria.messenger.core.helper.CommunityHelper;
import com.solaria.messenger.core.helper.LookupHelper;
import com.solaria.messenger.core.helper.NotificationHelper;
import com.solaria.messenger.core.model.ProjectCommunity;
import com.solaria.messenger.core.model.enums.CommunityStatus;
import com.solaria.messenger.core.repository.CommunityRepository;
import com.solaria.messenger.core.security.rbac.RbacAuthorizationService;
import com.solaria.messenger.core.support.ConversationMutationGuard;

import static com.solaria.messenger.core.helper.PureRulesHelper.nextVersion;
import static com.solaria.messenger.core.helper.PureRulesHelper.requireAdmin;
import static com.solaria.messenger.core.helper.PureRulesHelper.requireMember;
import static com.solaria.messenger.core.helper.PureRulesHelper.requireOwner;
import static com.solaria.messenger.core.mapper.CommunityMapper.toResponse;

@Service
public class UpdateCommunityStatusUseCase {

    private static final Logger log = LoggerFactory.getLogger(UpdateCommunityStatusUseCase.class);
    private final CommunityRepository communityRepository;
    private final RbacAuthorizationService rbac;
    private final LookupHelper lookup;
    private final CommunityHelper communities;
    private final NotificationHelper notifications;
    private final ConversationMutationGuard guard;

    public UpdateCommunityStatusUseCase(CommunityRepository communityRepository, RbacAuthorizationService rbac,
            LookupHelper lookup, CommunityHelper communities, NotificationHelper notifications,
            ConversationMutationGuard guard) {
        this.communityRepository = communityRepository;
        this.rbac = rbac;
        this.lookup = lookup;
        this.communities = communities;
        this.notifications = notifications;
        this.guard = guard;
    }

    /** Arquiva ou reativa a comunidade e avisa os subgrupos só se o status mudou */
    public CommunityResponseDTO execute(String id, CommunityUpdateRequestDTO dto) {
        if (dto == null || dto.getStatus() == null) {
            throw new InvalidFieldException("status é obrigatório.");
        }

        // o array guarda, de dentro da lambda, se a escrita realmente aconteceu
        boolean[] changed = { false };
        CommunityResponseDTO response = guard.execute(() -> updateCommunityLocked(id, dto, changed));
        if (changed[0]) publishCommunityConversations(id);
        return response;
    }

    /** Reativar exige ADMIN; arquivar exige OWNER; grava com CAS e tenta de novo se a versão mudou */
    private CommunityResponseDTO updateCommunityLocked(String id, CommunityUpdateRequestDTO dto, boolean[] changed) {
        CommunityStatus requested = dto.getStatus();
        UUID actor = rbac.currentUserId();

        ProjectCommunity updated = communities.mutateCommunity(lookup.requireCommunity(id), current -> {
            // confere o papel na versão mais recente
            requireMember(current, actor);
            if (requested == CommunityStatus.ACTIVE) {
                requireAdmin(current, actor);
            } else {
                requireOwner(current, actor);
            }

            // status já é o pedido: nada a gravar
            if (current.getStatus() == requested) return null;

            long modified = communityRepository.updateStatusIfCurrent(current.getId(), current.getVersion(), requested,
                    Instant.now(), nextVersion(current.getVersion()));
            if (modified == 1) changed[0] = true;
            return modified;
        });

        return toResponse(updated);
    }

    /** Avisa a mudança em todos os subgrupos de uma vez; falha aqui só gera log */
    private void publishCommunityConversations(String id) {
        try {
            Set<String> ids = communities.subgroupMetadata(id).stream()
                    .map(SubgroupMetadataResponseDTO::id).collect(Collectors.toSet());
            notifications.publishConversationsChanged(ids);
        } catch (RuntimeException failure) {
            log.warn("Falha ao publicar conversas da comunidade {}", id, failure);
        }
    }
}
