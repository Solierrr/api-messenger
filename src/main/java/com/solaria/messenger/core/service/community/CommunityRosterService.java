package com.solaria.messenger.core.service.community;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import com.solaria.messenger.core.contract.response.SubgroupMetadataResponseDTO;
import com.solaria.messenger.core.exception.ConcurrentUpdateException;
import com.solaria.messenger.core.exception.ResourceNotFoundException;
import com.solaria.messenger.core.exception.UnauthorizedAccessException;
import com.solaria.messenger.core.integration.core.ProjectAuthorityClient;
import com.solaria.messenger.core.integration.core.dto.ProjectAccessResponseDTO;
import com.solaria.messenger.core.model.CommunityMember;
import com.solaria.messenger.core.model.ProjectCommunity;
import com.solaria.messenger.core.model.enums.CommunityRole;
import com.solaria.messenger.core.model.enums.CommunityStatus;
import com.solaria.messenger.core.repository.CommunityRepository;
import com.solaria.messenger.core.service.ConversationMutationGuard;
import com.solaria.messenger.core.service.OptimisticMutation;
import com.solaria.messenger.core.service.conversation.ConversationChangeNotifier;
import com.solaria.messenger.core.service.conversation.ConversationQueryService;
import com.solaria.messenger.core.service.port.SessionRevoker;

/**
 * Service que deriva os membros e o status da comunidade do acesso ao projeto na api-core
 *
 * <p>
 * Os envolvidos do projeto viram membros com o papel equivalente ({@code OWNER -> OWNER}, {@code MANAGER -> ADMIN},
 * {@code MEMBER -> MEMBER}, {@code VIEWER -> VIEWER}) e projeto só para consulta deixa a comunidade arquivada.
 * Quem saiu do projeto perde a comunidade e as sessões abertas, mesmo que seja ele quem consultou.
 * </p>
 * <ul>
 *     <li>{@link #sync(ProjectCommunity, UUID)} -> consulta o projeto em nome do user e aplica o resultado.</li>
 *     <li>{@link #apply(ProjectCommunity, ProjectAccessResponseDTO)} -> aplica um acesso já consultado.</li>
 *     <li>{@link #membersOf(ProjectAccessResponseDTO, List, Instant)} -> monta os membros a partir do acesso.</li>
 * </ul>
 */
@Service
public class CommunityRosterService {

    private static final Logger log = LoggerFactory.getLogger(CommunityRosterService.class);
    private final CommunityRepository communityRepository;
    private final ProjectAuthorityClient projectAuthorityClient;
    private final ConversationQueryService queries;
    private final ConversationChangeNotifier stateEvents;
    private final ConversationMutationGuard guard;
    private final SessionRevoker sessionRevoker;

    public CommunityRosterService(CommunityRepository communityRepository, ProjectAuthorityClient projectAuthorityClient,
            ConversationQueryService queries, ConversationChangeNotifier stateEvents, ConversationMutationGuard guard,
            SessionRevoker sessionRevoker) {
        this.communityRepository = communityRepository;
        this.projectAuthorityClient = projectAuthorityClient;
        this.queries = queries;
        this.stateEvents = stateEvents;
        this.guard = guard;
        this.sessionRevoker = sessionRevoker;
    }

    /** Consulta o projeto em nome do user e alinha a comunidade; fora do projeto, o user sai da comunidade e recebe 403 */
    public ProjectCommunity sync(ProjectCommunity community, UUID actor) {
        try {
            return apply(community, projectAuthorityClient.access(community.getProjectId(), actor));
        } catch (UnauthorizedAccessException denied) {
            if (community.isMember(actor)) dropMember(community, actor);
            throw denied;
        }
    }

    /** Grava membros e status derivados com CAS; avisa os subgrupos e derruba as sessões de quem saiu */
    public ProjectCommunity apply(ProjectCommunity community, ProjectAccessResponseDTO access) {
        Set<UUID> removed = new HashSet<>();
        boolean[] changed = { false };

        ProjectCommunity updated = guard.execute(() -> OptimisticMutation.apply(community, current -> {
            List<CommunityMember> members = membersOf(access, current.getMembers(), Instant.now());
            CommunityStatus status = access.readOnly() ? CommunityStatus.ARCHIVED : CommunityStatus.ACTIVE;
            if (sameRoster(current.getMembers(), members) && current.getStatus() == status) return null;

            long modified = communityRepository.updateRosterIfCurrent(current.getId(), current.getVersion(), members,
                    status, Instant.now(), OptimisticMutation.nextVersion(current.getVersion()));
            if (modified == 1) {
                changed[0] = true;
                Set<UUID> remaining = members.stream().map(CommunityMember::getUserId).collect(Collectors.toSet());
                current.memberIds().stream().filter(user -> !remaining.contains(user)).forEach(removed::add);
            }
            return modified;
        }, () -> requireEntityById(community.getId()),
                () -> new ConcurrentUpdateException("A comunidade mudou durante a operação.")));

        if (changed[0]) publishCommunityConversations(updated.getId());
        removed.forEach(sessionRevoker::revokeUser);
        return updated;
    }

    /** Tira da comunidade quem a api-core já não reconhece no projeto e derruba as sessões dele */
    private void dropMember(ProjectCommunity community, UUID user) {
        boolean[] changed = { false };
        ProjectCommunity updated = guard.execute(() -> OptimisticMutation.apply(community, current -> {
            if (!current.isMember(user)) return null;
            List<CommunityMember> survivors = new ArrayList<>(current.getMembers());
            survivors.removeIf(member -> member.getUserId().equals(user));
            long modified = communityRepository.updateMembersIfCurrent(current.getId(), current.getVersion(), survivors,
                    Instant.now(), OptimisticMutation.nextVersion(current.getVersion()));
            if (modified == 1) changed[0] = true;
            return modified;
        }, () -> requireEntityById(community.getId()),
                () -> new ConcurrentUpdateException("A comunidade mudou durante a operação.")));

        if (changed[0]) publishCommunityConversations(updated.getId());
        sessionRevoker.revokeUser(user);
    }

    /** Membros com o papel do projeto, preservando a data de entrada de quem já era membro */
    public static List<CommunityMember> membersOf(ProjectAccessResponseDTO access, List<CommunityMember> current,
            Instant now) {
        Map<UUID, Instant> joined = current == null ? Map.of() : current.stream()
                .collect(Collectors.toMap(CommunityMember::getUserId, CommunityMember::getJoinedAt, (first, second) -> first));
        List<CommunityMember> members = new ArrayList<>();
        access.members().forEach((user, role) ->
                members.add(CommunityMember.of(user, roleOf(role), joined.getOrDefault(user, now))));
        return members;
    }

    /** Papel na comunidade equivalente ao papel no projeto */
    static CommunityRole roleOf(String projectRole) {
        return switch (projectRole) {
            case "OWNER" -> CommunityRole.OWNER;
            case "MANAGER" -> CommunityRole.ADMIN;
            case "VIEWER" -> CommunityRole.VIEWER;
            default -> CommunityRole.MEMBER;
        };
    }

    /** Mesmos membros com os mesmos papéis, sem depender da ordem */
    private static boolean sameRoster(List<CommunityMember> current, List<CommunityMember> desired) {
        if (current == null || current.size() != desired.size()) return false;
        Map<UUID, CommunityRole> before = current.stream()
                .collect(Collectors.toMap(CommunityMember::getUserId, CommunityMember::getRole, (first, second) -> first));
        Map<UUID, CommunityRole> after = desired.stream()
                .collect(Collectors.toMap(CommunityMember::getUserId, CommunityMember::getRole));
        return before.equals(after);
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
