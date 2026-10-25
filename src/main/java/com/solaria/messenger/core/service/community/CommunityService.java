package com.solaria.messenger.core.service.community;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import com.solaria.messenger.core.contract.request.CommunityRequestDTO;
import com.solaria.messenger.core.contract.request.CommunityUpdateRequestDTO;
import com.solaria.messenger.core.contract.response.CommunityResponseDTO;
import com.solaria.messenger.core.contract.response.GroupCreationResponseDTO;
import com.solaria.messenger.core.contract.response.SubgroupMetadataResponseDTO;
import com.solaria.messenger.core.exception.BusinessRuleException;
import com.solaria.messenger.core.exception.ResourceNotFoundException;
import com.solaria.messenger.core.exception.UnauthorizedAccessException;
import com.solaria.messenger.core.integration.core.ProjectAuthorityClient;
import com.solaria.messenger.core.integration.core.dto.ProjectAccessResponseDTO;
import com.solaria.messenger.core.model.ProjectCommunity;
import com.solaria.messenger.core.model.enums.CommunityRole;
import com.solaria.messenger.core.model.enums.CommunityStatus;
import com.solaria.messenger.core.repository.CommunityRepository;
import com.solaria.messenger.core.security.rbac.RbacAuthorizationService;
import com.solaria.messenger.core.service.conversation.ConversationCreationService;
import com.solaria.messenger.core.service.conversation.ConversationQueryService;

import static com.solaria.messenger.core.mapper.CommunityMapper.toResponse;
import static com.solaria.messenger.core.security.rbac.CommunityPolicy.requireMember;
import static com.solaria.messenger.core.security.rbac.CommunityPolicy.requireWriter;

/**
 * Service da comunidade do projeto
 *
 * <p>
 * Cria, consulta e lista os subgrupos visíveis da comunidade. Membros, papéis ({@link CommunityRole}) e status
 * vêm do projeto na api-core e são sincronizados por {@link CommunityRosterService} a cada acesso.
 * </p>
 * <ul>
 *     <li>{@link #createCommunity(CommunityRequestDTO)} -> cria a comunidade com os envolvidos do projeto, ou devolve a existente.</li>
 *     <li>{@link #findById(String)} -> busca uma comunidade da qual o user é membro.</li>
 *     <li>{@link #findByProjectId(UUID)} -> busca a comunidade de um projeto.</li>
 *     <li>{@link #findMine()} -> lista as comunidades do user.</li>
 *     <li>{@link #updateCommunity(String, CommunityUpdateRequestDTO)} -> recusado: a situação vem do projeto.</li>
 *     <li>{@link #startConversation(String, String, Set)} -> cria um subgrupo entre membros da comunidade.</li>
 *     <li>{@link #listConversations(String)} -> lista os subgrupos que o user pode ver.</li>
 * </ul>
 */
@Service
public class CommunityService {

    static final String MANAGED_BY_PROJECT = "Gerencie participantes e situação pela página do projeto.";

    private final CommunityRepository communityRepository;
    private final ConversationQueryService queries;
    private final ConversationCreationService creations;
    private final RbacAuthorizationService rbac;
    private final ProjectAuthorityClient projectAuthorityClient;
    private final CommunityRosterService roster;

    public CommunityService(CommunityRepository communityRepository, ConversationQueryService queries,
            ConversationCreationService creations, RbacAuthorizationService rbac,
            ProjectAuthorityClient projectAuthorityClient, CommunityRosterService roster) {
        this.communityRepository = communityRepository;
        this.queries = queries;
        this.creations = creations;
        this.rbac = rbac;
        this.projectAuthorityClient = projectAuthorityClient;
        this.roster = roster;
    }

    /** Cria a comunidade com os envolvidos do projeto; se ela já existe, sincroniza e devolve a existente */
    public CommunityResponseDTO createCommunity(CommunityRequestDTO dto) {
        UUID currentUserId = rbac.currentUserId();

        ProjectAccessResponseDTO access = projectAuthorityClient.access(dto.getProjectId(), currentUserId);
        ProjectCommunity existing = communityRepository.findByProjectId(dto.getProjectId()).orElse(null);
        if (existing != null) return toResponse(roster.apply(existing, access));

        Instant now = Instant.now();
        ProjectCommunity community = new ProjectCommunity();
        community.setProjectId(dto.getProjectId());
        community.setMembers(new ArrayList<>(CommunityRosterService.membersOf(access, null, now)));
        community.setStatus(access.readOnly() ? CommunityStatus.ARCHIVED : CommunityStatus.ACTIVE);
        community.setCreatedBy(currentUserId);
        community.setCreatedAt(now);
        community.setUpdatedAt(now);
        try {
            return toResponse(roster.apply(communityRepository.save(community), access));
        } catch (DuplicateKeyException collision) {
            ProjectCommunity winner = communityRepository.findByProjectId(dto.getProjectId()).orElseThrow(() -> collision);
            return toResponse(roster.apply(winner, access));
        }
    }

    /** Busca uma comunidade da qual o user é membro */
    public CommunityResponseDTO findById(String id) {
        return toResponse(requireSyncedMember(requireEntityById(id)));
    }

    /** Busca a comunidade de um projeto da qual o user é membro */
    public CommunityResponseDTO findByProjectId(UUID projectId) {
        ProjectCommunity community = communityRepository.findByProjectId(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Comunidade não encontrada para o projeto: " + projectId));
        return toResponse(requireSyncedMember(community));
    }

    /** Lista as comunidades do user autenticado, deixando de fora os projetos dos quais ele saiu */
    public List<CommunityResponseDTO> findMine() {
        UUID actor = rbac.currentUserId();
        List<CommunityResponseDTO> mine = new ArrayList<>();
        for (ProjectCommunity community : communityRepository.findAllByMemberId(actor)) {
            ProjectCommunity synced = syncedOrNull(community, actor);
            if (synced != null && synced.isMember(actor)) mine.add(toResponse(synced));
        }
        return mine;
    }

    /** Comunidade sincronizada, ou {@code null} quando o user já saiu do projeto */
    private ProjectCommunity syncedOrNull(ProjectCommunity community, UUID actor) {
        try {
            return roster.sync(community, actor);
        } catch (UnauthorizedAccessException removed) {
            return null;
        }
    }

    /** Status e membros seguem o projeto, então a comunidade não é alterada por aqui */
    public CommunityResponseDTO updateCommunity(String id, CommunityUpdateRequestDTO dto) {
        throw new BusinessRuleException(MANAGED_BY_PROJECT);
    }

    /** Cria um subgrupo; o criador precisa poder escrever no projeto */
    public GroupCreationResponseDTO startConversation(String id, String title, Set<UUID> participantIds) {
        ProjectCommunity community = requireSyncedMember(requireEntityById(id));
        requireWriter(community, rbac.currentUserId());
        return creations.createCommunityGroupConversation(community.getId(), title, participantIds);
    }

    /** OWNER/ADMIN veem todos os subgrupos; os demais membros veem só os que participam */
    public List<SubgroupMetadataResponseDTO> listConversations(String id) {
        ProjectCommunity community = requireSyncedMember(requireEntityById(id));
        UUID actor = rbac.currentUserId();
        return community.isAdmin(actor) ? queries.findSubgroupMetadataByCommunity(community.getId())
                : queries.findSubgroupMetadataByCommunityAndParticipant(community.getId(), actor);
    }

    /** Sincroniza a comunidade com o projeto e exige que o user continue membro */
    private ProjectCommunity requireSyncedMember(ProjectCommunity community) {
        UUID actor = rbac.currentUserId();
        ProjectCommunity synced = roster.sync(community, actor);
        requireMember(synced, actor);
        return synced;
    }

    private ProjectCommunity requireEntityById(String id) {
        return communityRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Comunidade não encontrada com id: " + id));
    }
}
