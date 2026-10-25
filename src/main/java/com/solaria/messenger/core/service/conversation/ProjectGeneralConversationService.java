package com.solaria.messenger.core.service.conversation;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;

import com.solaria.messenger.core.model.CommunityMember;
import com.solaria.messenger.core.model.Conversation;
import com.solaria.messenger.core.model.ProjectCommunity;
import com.solaria.messenger.core.model.enums.CommunityRole;
import com.solaria.messenger.core.model.enums.CommunityStatus;
import com.solaria.messenger.core.model.enums.ConversationStatus;
import com.solaria.messenger.core.model.enums.ConversationType;
import com.solaria.messenger.core.repository.ConversationRepository;
import com.solaria.messenger.core.service.ConversationMutationGuard;

/**
 * Service da conversa geral do projeto
 *
 * <p>
 * Cada comunidade tem uma conversa geral com id fixo ({@code project-general-<communityId>}), da qual participam
 * todos os membros da comunidade. Ela é criada quando a comunidade ativa tem ao menos dois membros e acompanha
 * os envolvidos do projeto a cada sincronização; participantes não são geridos à mão.
 * </p>
 * <ul>
 *     <li>{@link #ensure(ProjectCommunity, String)} -> cria ou alinha a conversa geral da comunidade.</li>
 *     <li>{@link #idOf(String)} -> id fixo da conversa geral de uma comunidade.</li>
 * </ul>
 */
@Service
public class ProjectGeneralConversationService {

    private static final String ID_PREFIX = "project-general-";
    private static final String TITLE_PREFIX = "Geral · ";

    private final ConversationRepository conversationRepository;
    private final ConversationAdmissionService admissions;
    private final ConversationChangeNotifier stateEvents;
    private final ConversationMutationGuard guard;

    public ProjectGeneralConversationService(ConversationRepository conversationRepository,
            ConversationAdmissionService admissions, ConversationChangeNotifier stateEvents,
            ConversationMutationGuard guard) {
        this.conversationRepository = conversationRepository;
        this.admissions = admissions;
        this.stateEvents = stateEvents;
        this.guard = guard;
    }

    /** Id fixo, para que duas instâncias nunca criem duas conversas gerais para a mesma comunidade */
    public static String idOf(String communityId) {
        return ID_PREFIX + communityId;
    }

    /**
     * Cria a conversa geral ou alinha participantes, gestor e título aos membros atuais da comunidade.
     * Sem o nome do projeto, mantém o título atual e não cria a conversa.
     */
    public void ensure(ProjectCommunity community, String projectName) {
        String id = idOf(community.getId());
        Set<UUID> removed = new LinkedHashSet<>();
        boolean changed = guard.execute(() -> ensureLocked(id, community, projectName, removed));

        removed.forEach(user -> stateEvents.publishRemovedConversation(user, id));
        if (changed) stateEvents.publishConversationChanged(id);
    }

    /** Grava dentro do guard; devolve se algo mudou e anota quem saiu, para avisar fora dele */
    private boolean ensureLocked(String id, ProjectCommunity community, String projectName, Set<UUID> removed) {
        Set<UUID> participants = new LinkedHashSet<>(community.memberIds());
        UUID manager = managerOf(community);

        Conversation current = conversationRepository.findById(id).orElse(null);
        if (current == null) {
            if (projectName == null || community.getStatus() != CommunityStatus.ACTIVE || participants.size() < 2
                    || manager == null) {
                return false;
            }
            try {
                admissions.createWithInitialStates(
                        newGeneral(id, community.getId(), manager, participants, TITLE_PREFIX + projectName));
                return true;
            } catch (DuplicateKeyException collision) {
                return false;
            }
        }

        String title = projectName == null ? current.getTitle() : TITLE_PREFIX + projectName;
        Conversation updated = admissions.syncProjectGeneral(id, participants, manager, title);
        if (updated == null) return false;
        current.getParticipantIds().stream().filter(user -> !updated.hasParticipant(user)).forEach(removed::add);
        return true;
    }

    /** Quem criou a comunidade, se ainda for membro; senão o primeiro OWNER, ADMIN ou membro que restar */
    private static UUID managerOf(ProjectCommunity community) {
        if (community.isMember(community.getCreatedBy())) return community.getCreatedBy();
        for (CommunityRole role : CommunityRole.values()) {
            for (CommunityMember member : community.getMembers()) {
                if (member.getRole() == role) return member.getUserId();
            }
        }
        return null;
    }

    private static Conversation newGeneral(String id, String communityId, UUID manager, Set<UUID> participants,
            String title) {
        Instant now = Instant.now();
        Conversation conversation = new Conversation();
        conversation.setId(id);
        conversation.setConversationType(ConversationType.GROUP);
        conversation.setParticipantIds(participants);
        conversation.setCreatedBy(manager);
        conversation.setManagerUserId(manager);
        conversation.setTitle(title);
        conversation.setCommunityId(communityId);
        conversation.setProjectGeneral(true);
        conversation.setStatus(ConversationStatus.ACTIVE);
        conversation.setStartedAt(now);
        conversation.setLastInteractionAt(now);
        return conversation;
    }
}
