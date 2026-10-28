package com.solaria.messenger.core.helper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.solaria.messenger.core.exception.ResourceNotFoundException;
import com.solaria.messenger.core.exception.UnauthorizedAccessException;
import com.solaria.messenger.core.model.Conversation;
import com.solaria.messenger.core.model.ProjectCommunity;
import com.solaria.messenger.core.repository.CommunityRepository;
import com.solaria.messenger.core.repository.ConversationRepository;

@Component
public class ConversationAccessHelper {

    private final ConversationRepository conversations;
    private final CommunityRepository communities;
    private final LookupHelper lookup;

    public ConversationAccessHelper(ConversationRepository conversations, CommunityRepository communities, LookupHelper lookup) {
        this.conversations = conversations;
        this.communities = communities;
        this.lookup = lookup;
    }

    /** Carrega a conversa e exige que o user participe dela (e da comunidade, se for subgrupo) */
    public Conversation requireParticipant(String conversationId, UUID actor) {
        Conversation conversation = conversations.findById(conversationId)
                .orElseThrow(() -> new ResourceNotFoundException("Conversa não encontrada: " + conversationId));
        if (!conversation.hasParticipant(actor)) {
            throw new UnauthorizedAccessException("O usuário não participa desta conversa.");
        }

        // vale também no meio de uma remoção da comunidade: fora dela, sem acesso ao subgrupo
        if (conversation.getCommunityId() != null) {
            ProjectCommunity community = communities.findById(conversation.getCommunityId())
                    .orElseThrow(() -> new ResourceNotFoundException("Comunidade não encontrada."));
            if (!community.isMember(actor)) {
                throw new UnauthorizedAccessException("O usuário não participa desta comunidade.");
            }
        }
        return conversation;
    }

    /** Mesma checagem para quem já tem a conversa em mãos; relê do banco para usar a participação atual */
    public void requireParticipant(Conversation conversation, UUID actor) {
        Conversation latest = conversation.getId() == null ? conversation : lookup.requireConversation(conversation.getId());
        if (!latest.hasParticipant(actor)) {
            throw new UnauthorizedAccessException("O objeto da operação não foi encontrado.");
        }
        ProjectCommunity community = latest.getCommunityId() == null ? null : lookup.requireParentCommunity(latest.getCommunityId());
        if (community != null && !community.isMember(actor)) {
            throw new UnauthorizedAccessException("O usuário não participa desta comunidade.");
        }
    }

    /** Quem pode ver cada conversa do lote, carregando as comunidades uma vez só */
    public Map<String, Set<UUID>> authorizedParticipants(List<Conversation> current) {
        return authorizedParticipants(current, communitiesFor(current));
    }

    /** Comunidades das conversas, carregadas juntas para reaproveitar na autorização e na projeção */
    public Map<String, ProjectCommunity> communitiesFor(List<Conversation> current) {
        Set<String> parentIds = current.stream().map(Conversation::getCommunityId)
                .filter(Objects::nonNull).collect(Collectors.toSet());
        Map<String, ProjectCommunity> parents = new LinkedHashMap<>();
        if (!parentIds.isEmpty()) communities.findAllById(parentIds).forEach(parent -> parents.put(parent.getId(), parent));
        return parents;
    }

    /** Por conversa, mantém só os participantes que também são membros atuais da comunidade */
    public Map<String, Set<UUID>> authorizedParticipants(List<Conversation> current, Map<String, ProjectCommunity> parents) {
        Map<String, Set<UUID>> result = new LinkedHashMap<>();
        for (Conversation conversation : current) {
            ProjectCommunity parent = parents.get(conversation.getCommunityId());
            Set<UUID> users = conversation.getParticipantIds().stream()
                    .filter(user -> conversation.getCommunityId() == null || parent != null && parent.isMember(user))
                    .collect(Collectors.toSet());
            result.put(conversation.getId(), Set.copyOf(users));
        }
        return result;
    }

    /** Recarrega as conversas pelos ids e refaz a autorização (usado logo antes de publicar) */
    public Map<String, Set<UUID>> authorizedParticipants(Set<String> ids) {
        return authorizedParticipants(conversations.findAllById(ids));
    }

    /** Autoriza o user e passa a conversa já carregada para a ação */
    public <T> T withAccess(String conversationId, UUID actor, Function<Conversation, T> action) {
        return action.apply(requireParticipant(conversationId, actor));
    }

    /** Comunidade arquivada bloqueia mensagens e administração dos subgrupos */
    public void requireCommunityActive(Conversation conversation) {
        if (conversation.getCommunityId() == null) return;

        ProjectCommunity community = communities.findById(conversation.getCommunityId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Comunidade não encontrada para a conversa: " + conversation.getId()));
        // nome qualificado: o método desta classe esconde o import estático de mesmo nome
        PureRulesHelper.requireCommunityActive(community);
    }
}
