package com.solaria.messenger.core.helper;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;

import org.bson.types.ObjectId;

import com.solaria.messenger.core.exception.BusinessRuleException;
import com.solaria.messenger.core.exception.InvalidFieldException;
import com.solaria.messenger.core.exception.UnauthorizedAccessException;
import com.solaria.messenger.core.model.CommunityMember;
import com.solaria.messenger.core.model.Conversation;
import com.solaria.messenger.core.model.ProjectCommunity;
import com.solaria.messenger.core.model.enums.CommunityStatus;
import com.solaria.messenger.core.model.enums.ConversationStatus;
import com.solaria.messenger.core.model.enums.ConversationType;

public final class PureRulesHelper {

    private static final int MAX_ATTEMPTS = 3;

    private PureRulesHelper() { }

    // CAS

    /**
     * Aplica a escrita até 3 vezes, relendo o documento entre as tentativas.
     * A mutação devolve {@code null} quando não há nada a gravar, {@code 1} quando gravou e outro valor quando o CAS não gravou.
     */
    public static <T> T applyOptimistic(T initial, Function<T, Long> mutation, Supplier<T> reload,
            Supplier<? extends RuntimeException> exhausted) {
        T current = initial;
        for (int attempt = 0; attempt < MAX_ATTEMPTS; attempt++) {
            Long modified = mutation.apply(current);
            if (modified == null) return current;

            // relê nos dois casos: gravou (devolve o novo estado) ou perdeu para outra escrita (tenta de novo)
            current = reload.get();
            if (modified == 1) return current;
        }
        throw exhausted.get();
    }

    /** Próxima versão do CAS; documento antigo sem versão começa em zero */
    public static Long nextVersion(Long version) {
        return version == null ? 0L : Math.addExact(version, 1L);
    }

    // comunidade

    /** Exige que o ator seja membro da comunidade. */
    public static void requireMember(ProjectCommunity community, UUID actor) {
        if (!community.isMember(actor)) {
            throw new UnauthorizedAccessException("O usuário autenticado não participa desta comunidade.");
        }
    }

    /** Exige papel OWNER ou ADMIN na comunidade. */
    public static void requireAdmin(ProjectCommunity community, UUID actor) {
        if (!community.isAdmin(actor)) {
            throw new UnauthorizedAccessException("Esta ação exige papel OWNER ou ADMIN na comunidade.");
        }
    }

    /** Exige papel OWNER na comunidade. */
    public static void requireOwner(ProjectCommunity community, UUID actor) {
        if (!community.isOwner(actor)) {
            throw new UnauthorizedAccessException("Esta ação exige papel OWNER na comunidade.");
        }
    }

    /** A limpeza de uma participação já revogada só retira resíduos, nunca concede acesso ou gestão. */
    public static void requireRemovalAuthority(ProjectCommunity community, UUID actor, UUID target) {
        if (community.isOwner(target)) {
            throw new BusinessRuleException("O dono da comunidade não pode ser removido.");
        }
        if (!actor.equals(target) && (!community.isMember(actor) || !community.isAdmin(actor))) {
            throw new UnauthorizedAccessException("A remoção de terceiros exige OWNER ou ADMIN atuais.");
        }
        if (community.isMember(target) && !actor.equals(target)) requireCommunityActive(community);
    }

    public static void requireCommunityActive(ProjectCommunity community) {
        if (community.getStatus() != CommunityStatus.ACTIVE) {
            throw new BusinessRuleException("A comunidade está arquivada.");
        }
    }

    /** Cópia dos membros, para montar a escrita sem alterar o documento lido */
    public static List<CommunityMember> copyMembers(List<CommunityMember> members) {
        return members == null ? new ArrayList<>() : members.stream()
                .map(member -> CommunityMember.of(member.getUserId(), member.getRole(), member.getJoinedAt()))
                .collect(Collectors.toCollection(ArrayList::new));
    }

    // conversa

    /** Conversa desativada não aceita comandos, mas continua podendo ser lida */
    public static void requireConversationActive(Conversation conversation) {
        if (conversation.getStatus() != ConversationStatus.ACTIVE) {
            throw new BusinessRuleException("A conversa está desativada e não aceita novas mensagens.");
        }
    }

    /** Monta uma conversa ativa com id próprio; em grupo, o criador começa como gestor */
    public static Conversation newConversation(ConversationType type, UUID createdBy, Set<UUID> participants) {
        Instant now = Instant.now();
        Conversation conversation = new Conversation();
        conversation.setId(new ObjectId().toHexString());
        conversation.setConversationType(type);
        conversation.setParticipantIds(participants);
        conversation.setCreatedBy(createdBy);
        if (type == ConversationType.GROUP) conversation.setManagerUserId(createdBy);
        conversation.setStatus(ConversationStatus.ACTIVE);
        conversation.setStartedAt(now);
        conversation.setLastInteractionAt(now);
        return conversation;
    }

    /** Chave do par que não depende da ordem dos ids: ids ordenados e unidos por "|" */
    public static String directKey(Collection<UUID> users) {
        return String.join("|", users.stream().map(UUID::toString).sorted().toList());
    }

    public static void requireNoNullParticipants(Set<UUID> participants) {
        if (participants.contains(null)) throw new InvalidFieldException("Participantes não podem ser nulos.");
    }

    public static Set<UUID> withoutUser(Set<UUID> participants, UUID user) {
        Set<UUID> survivors = new LinkedHashSet<>(participants);
        survivors.remove(user);
        return survivors;
    }
}
