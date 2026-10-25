package com.solaria.messenger.core.service.community;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.solaria.messenger.core.contract.request.CommunityRequestDTO;
import com.solaria.messenger.core.contract.request.CommunityUpdateRequestDTO;
import com.solaria.messenger.core.contract.response.CommunityResponseDTO;
import com.solaria.messenger.core.contract.response.SubgroupMetadataResponseDTO;
import com.solaria.messenger.core.exception.ConcurrentUpdateException;
import com.solaria.messenger.core.exception.DuplicateResourceException;
import com.solaria.messenger.core.exception.InvalidFieldException;
import com.solaria.messenger.core.exception.ResourceNotFoundException;
import com.solaria.messenger.core.integration.core.ProjectAuthorityClient;
import com.solaria.messenger.core.mapper.CommunityMapper;
import com.solaria.messenger.core.model.CommunityMember;
import com.solaria.messenger.core.model.ProjectCommunity;
import com.solaria.messenger.core.model.enums.CommunityRole;
import com.solaria.messenger.core.model.enums.CommunityStatus;
import com.solaria.messenger.core.repository.CommunityRepository;
import com.solaria.messenger.core.security.rbac.RbacAuthorizationService;
import com.solaria.messenger.core.service.ConversationMutationGuard;
import com.solaria.messenger.core.service.OptimisticMutation;
import com.solaria.messenger.core.service.UserAccessService;
import com.solaria.messenger.core.service.conversation.ConversationChangeNotifier;
import com.solaria.messenger.core.service.conversation.ConversationQueryService;

import static com.solaria.messenger.core.mapper.CommunityMapper.toResponse;
import static com.solaria.messenger.core.security.rbac.CommunityPolicy.requireAdmin;
import static com.solaria.messenger.core.security.rbac.CommunityPolicy.requireMember;
import static com.solaria.messenger.core.security.rbac.CommunityPolicy.requireOwner;

/**
 * Service da comunidade do projeto
 *
 * <p>
 * Cria, consulta, arquiva e reativa a comunidade e lista os subgrupos visíveis.
 * Cada operação depende do papel ({@link CommunityRole}) do user; membros ficam em {@link CommunityMembershipService}.
 * </p>
 * <ul>
 *     <li>{@link #createCommunity(CommunityRequestDTO)} -> cria a comunidade com o demandante como OWNER.</li>
 *     <li>{@link #findById(String)} -> busca uma comunidade da qual o user é membro.</li>
 *     <li>{@link #findByProjectId(UUID)} -> busca a comunidade de um projeto.</li>
 *     <li>{@link #findMine()} -> lista as comunidades do user.</li>
 *     <li>{@link #updateCommunity(String, CommunityUpdateRequestDTO)} -> arquiva ou reativa a comunidade.</li>
 *     <li>{@link #listConversations(String)} -> lista os subgrupos que o user pode ver.</li>
 * </ul>
 */
@Service
public class CommunityService {

    private static final Logger log = LoggerFactory.getLogger(CommunityService.class);
    private final CommunityRepository communityRepository;
    private final ConversationQueryService queries;
    private final ConversationChangeNotifier stateEvents;
    private final ConversationMutationGuard guard;
    private final RbacAuthorizationService rbac;
    private final ProjectAuthorityClient projectAuthorityClient;
    private final UserAccessService userAccessService;

    public CommunityService(CommunityRepository communityRepository, ConversationQueryService queries,
            ConversationChangeNotifier stateEvents, ConversationMutationGuard guard, RbacAuthorizationService rbac,
            ProjectAuthorityClient projectAuthorityClient, UserAccessService userAccessService) {
        this.communityRepository = communityRepository;
        this.queries = queries;
        this.stateEvents = stateEvents;
        this.guard = guard;
        this.rbac = rbac;
        this.projectAuthorityClient = projectAuthorityClient;
        this.userAccessService = userAccessService;
    }

    /** Cria a comunidade do projeto com o demandante como OWNER e os membros iniciais como MEMBER */
    public CommunityResponseDTO createCommunity(CommunityRequestDTO dto) {
        UUID currentUserId = rbac.currentUserId();

        // só o demandante do projeto cria, e cada projeto tem uma comunidade só
        projectAuthorityClient.requireRequester(dto.getProjectId(), currentUserId);
        if (communityRepository.existsByProjectId(dto.getProjectId())) {
            throw new DuplicateResourceException("Já existe uma comunidade para o projeto: " + dto.getProjectId());
        }

        // membros iniciais sem nulos e sem o próprio criador, todos precisam estar ativos
        Set<UUID> memberIds = new LinkedHashSet<>();
        if (dto.getMemberIds() != null) {
            dto.getMemberIds().stream()
                    .filter(Objects::nonNull)
                    .filter(id -> !id.equals(currentUserId))
                    .forEach(memberIds::add);
        }
        userAccessService.requireActiveUsers(memberIds);

        // monta a lista com o criador como OWNER e os demais como MEMBER
        Instant now = Instant.now();
        List<CommunityMember> members = new ArrayList<>();
        members.add(CommunityMember.of(currentUserId, CommunityRole.OWNER, now));
        memberIds.forEach(id -> members.add(CommunityMember.of(id, CommunityRole.MEMBER, now)));

        // monta e salva a comunidade ativa
        ProjectCommunity community = new ProjectCommunity();
        community.setProjectId(dto.getProjectId());
        community.setMembers(members);
        community.setStatus(CommunityStatus.ACTIVE);
        community.setCreatedBy(currentUserId);
        community.setCreatedAt(now);
        community.setUpdatedAt(now);
        return toResponse(communityRepository.save(community));
    }

    /** Busca uma comunidade da qual o user é membro */
    public CommunityResponseDTO findById(String id) {
        ProjectCommunity community = requireEntityById(id);
        requireMember(community, rbac.currentUserId());
        return toResponse(community);
    }

    /** Busca a comunidade de um projeto da qual o user é membro */
    public CommunityResponseDTO findByProjectId(UUID projectId) {
        ProjectCommunity community = communityRepository.findByProjectId(projectId)
                .orElseThrow(() -> new ResourceNotFoundException("Comunidade não encontrada para o projeto: " + projectId));
        requireMember(community, rbac.currentUserId());
        return toResponse(community);
    }

    /** Lista as comunidades do user autenticado */
    public List<CommunityResponseDTO> findMine() {
        return communityRepository.findAllByMemberId(rbac.currentUserId()).stream()
                .map(CommunityMapper::toResponse)
                .toList();
    }

    /** Arquiva ou reativa a comunidade e avisa os subgrupos só se o status mudou */
    public CommunityResponseDTO updateCommunity(String id, CommunityUpdateRequestDTO dto) {
        if (dto == null || dto.getStatus() == null) {
            throw new InvalidFieldException("status é obrigatório.");
        }

        // o array guarda, de dentro da lambda, se a escrita realmente aconteceu
        boolean[] changed = { false };
        CommunityResponseDTO response = guard.execute(() -> updateCommunityLocked(id, dto, changed));
        if (changed[0]) publishCommunityConversations(id);
        return response;
    }

    /** OWNER/ADMIN veem todos os subgrupos; membro comum vê só os que participa */
    public List<SubgroupMetadataResponseDTO> listConversations(String id) {
        ProjectCommunity community = requireEntityById(id);
        UUID actor = rbac.currentUserId();
        requireMember(community, actor);
        return community.isAdmin(actor) ? queries.findSubgroupMetadataByCommunity(community.getId())
                : queries.findSubgroupMetadataByCommunityAndParticipant(community.getId(), actor);
    }

    /** Reativar exige ADMIN; arquivar exige OWNER; grava com CAS e tenta de novo se a versão mudou */
    private CommunityResponseDTO updateCommunityLocked(String id, CommunityUpdateRequestDTO dto, boolean[] changed) {
        CommunityStatus requested = dto.getStatus();
        UUID actor = rbac.currentUserId();

        ProjectCommunity updated = OptimisticMutation.apply(requireEntityById(id), current -> {
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
                    Instant.now(), OptimisticMutation.nextVersion(current.getVersion()));
            if (modified == 1) changed[0] = true;
            return modified;
        }, () -> requireEntityById(id), () -> new ConcurrentUpdateException("A comunidade mudou durante a operação."));

        return toResponse(updated);
    }

    /** Avisa a mudança em todos os subgrupos de uma vez; falha aqui só gera log */
    private void publishCommunityConversations(String id) {
        try {
            Set<String> ids = queries.findSubgroupMetadataByCommunity(id).stream()
                    .map(SubgroupMetadataResponseDTO::id).collect(Collectors.toSet());
            stateEvents.publishConversationsChanged(ids);
        } catch (RuntimeException failure) {
            log.warn("Falha ao publicar conversas da comunidade {}", id, failure);
        }
    }

    private ProjectCommunity requireEntityById(String id) {
        return communityRepository.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Comunidade não encontrada com id: " + id));
    }
}
