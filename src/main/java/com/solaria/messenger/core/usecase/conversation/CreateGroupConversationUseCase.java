package com.solaria.messenger.core.usecase.conversation;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.solaria.messenger.core.contract.response.GroupCreationResponseDTO;
import com.solaria.messenger.core.contract.response.RejectedParticipantDTO;
import com.solaria.messenger.core.exception.BusinessRuleException;
import com.solaria.messenger.core.exception.UnauthorizedAccessException;
import com.solaria.messenger.core.helper.ConversationAdmissionHelper;
import com.solaria.messenger.core.helper.LookupHelper;
import com.solaria.messenger.core.helper.NotificationHelper;
import com.solaria.messenger.core.helper.UserAccessHelper;
import com.solaria.messenger.core.model.Conversation;
import com.solaria.messenger.core.model.ProjectCommunity;
import com.solaria.messenger.core.model.enums.ConversationType;
import com.solaria.messenger.core.security.rbac.RbacAuthorizationService;
import com.solaria.messenger.core.support.ConversationMutationGuard;

import static com.solaria.messenger.core.helper.PureRulesHelper.newConversation;
import static com.solaria.messenger.core.helper.PureRulesHelper.requireCommunityActive;
import static com.solaria.messenger.core.helper.PureRulesHelper.requireNoNullParticipants;
import static com.solaria.messenger.core.mapper.ConversationMapper.toResponse;

@Service
public class CreateGroupConversationUseCase {

    private final RbacAuthorizationService rbac;
    private final UserAccessHelper users;
    private final LookupHelper lookup;
    private final ConversationAdmissionHelper admissions;
    private final NotificationHelper notifications;
    private final ConversationMutationGuard guard;

    public CreateGroupConversationUseCase(RbacAuthorizationService rbac, UserAccessHelper users, LookupHelper lookup,
            ConversationAdmissionHelper admissions, NotificationHelper notifications, ConversationMutationGuard guard) {
        this.rbac = rbac;
        this.users = users;
        this.lookup = lookup;
        this.admissions = admissions;
        this.notifications = notifications;
        this.guard = guard;
    }

    /** Cria o grupo dentro do guard e avisa os participantes depois, fora dele */
    public GroupCreationResponseDTO execute(String communityId, String title, Set<UUID> requested) {
        GroupCreationResponseDTO result = guard.execute(() ->
                createSelectedGroup(communityId, title, rbac.currentUserId(), requested));
        notifications.publishConversationChanged(result.conversation().getId());
        return result;
    }

    /** Monta o grupo com os elegíveis; só inelegibilidade conhecida vira rejeição, falha técnica sobe como erro */
    private GroupCreationResponseDTO createSelectedGroup(String communityId, String title, UUID creator,
            Set<UUID> requestedIds) {
        // criador ativo; em subgrupo, ele precisa ser membro de uma comunidade ativa
        users.requireActiveActor(creator);
        ProjectCommunity parent = communityId == null ? null : lookup.requireParentCommunity(communityId);
        if (parent != null && !parent.isMember(creator)) {
            throw new UnauthorizedAccessException("O criador não participa da comunidade.");
        }
        if (parent != null) requireCommunityActive(parent);

        // subgrupo sem convidados recebe todos os membros da comunidade
        Set<UUID> requested = new LinkedHashSet<>();
        if (requestedIds != null) requested.addAll(requestedIds);
        if (parent != null && requested.isEmpty()) requested.addAll(parent.memberIds());
        requireNoNullParticipants(requested);

        // candidatos: convidados sem o criador e, em subgrupo, só membros da comunidade
        Set<UUID> candidates = new LinkedHashSet<>(requested);
        candidates.remove(creator);
        if (parent != null) candidates.retainAll(parent.memberIds());

        // aceitos: criador + candidatos elegíveis; o grupo precisa de pelo menos dois
        Set<UUID> accepted = new LinkedHashSet<>();
        accepted.add(creator);
        accepted.addAll(users.eligibleUsers(candidates));
        if (accepted.size() < 2) {
            throw new BusinessRuleException("Um grupo exige o criador e ao menos outro participante elegível.");
        }
        List<RejectedParticipantDTO> rejected = requested.stream().filter(user -> !accepted.contains(user))
                .map(user -> new RejectedParticipantDTO(user, "PARTICIPANT_NOT_ADMITTED")).toList();

        // grava o grupo e devolve aceitos e rejeitados
        Conversation conversation = newConversation(ConversationType.GROUP, creator, accepted);
        conversation.setTitle(title);
        conversation.setCommunityId(communityId);
        Conversation saved = admissions.persistNew(conversation);
        return new GroupCreationResponseDTO(toResponse(saved), saved.getParticipantIds(), rejected);
    }
}
