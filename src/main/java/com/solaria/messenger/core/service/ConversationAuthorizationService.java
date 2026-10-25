package com.solaria.messenger.core.service;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;

import com.solaria.messenger.core.exception.BusinessException;
import com.solaria.messenger.core.exception.BusinessRuleException;
import com.solaria.messenger.core.exception.MessageMutationUncertainException;
import com.solaria.messenger.core.exception.ResourceNotFoundException;
import com.solaria.messenger.core.exception.UnauthorizedAccessException;
import com.solaria.messenger.core.model.Conversation;
import com.solaria.messenger.core.model.ProjectCommunity;
import com.solaria.messenger.core.model.enums.CommunityStatus;
import com.solaria.messenger.core.model.enums.ConversationStatus;
import com.solaria.messenger.core.model.enums.ConversationType;
import com.solaria.messenger.core.repository.CommunityRepository;
import com.solaria.messenger.core.repository.ConversationRepository;

import static com.solaria.messenger.core.security.rbac.CommunityPolicy.requireWriter;

/**
 * Service de autorização de conversas
 *
 * <p>
 * Decide quem pode acessar cada conversa olhando a participação gravada no banco, não o socket aberto.
 * Em subgrupo, o user também precisa ser membro da comunidade.
 * </p>
 * <ul>
 *     <li>{@link #requireParticipant(String, UUID)} -> carrega a conversa e exige que o user participe.</li>
 *     <li>{@link #authorizedParticipants(List)} -> quem pode ver cada conversa de um lote.</li>
 *     <li>{@link #communitiesFor(List)} -> carrega de uma vez as comunidades das conversas.</li>
 *     <li>{@link #withAccess(String, UUID, Function)} -> executa uma ação com a conversa já autorizada.</li>
 *     <li>{@link #withMessageMutation(String, UUID, Function)} -> autoriza e executa a edição/exclusão de mensagem.</li>
 *     <li>{@link #requireActive(Conversation)} / {@link #requireCommunityActive(Conversation)} -> bloqueiam escrita em conversa ou comunidade inativa.</li>
 *     <li>{@link #requireCommunityWriter(Conversation, UUID)} -> além da comunidade ativa, exige um membro que possa escrever.</li>
 * </ul>
 */
@Service
public class ConversationAuthorizationService {

    private final ConversationRepository conversations;
    private final CommunityRepository communities;
    private final ConversationMutationGuard guard;

    public ConversationAuthorizationService(ConversationRepository conversations, CommunityRepository communities, ConversationMutationGuard guard) {
        this.conversations = conversations;
        this.communities = communities;
        this.guard = guard;
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
        Conversation latest = conversation.getId() == null ? conversation : requireEntityById(conversation.getId());
        if (!latest.hasParticipant(actor)) {
            throw new UnauthorizedAccessException("O objeto da operação não foi encontrado.");
        }
        ProjectCommunity community = latest.getCommunityId() == null ? null : requireCommunity(latest.getCommunityId());
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

    public Conversation requireEntityById(String id) {
        return conversations.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Conversa não encontrada com id: " + id));
    }

    public ProjectCommunity requireCommunity(String id) {
        return communities.findById(id)
                .orElseThrow(() -> new ResourceNotFoundException("Comunidade não encontrada: " + id));
    }

    /** Conversa desativada não aceita comandos, mas continua podendo ser lida */
    public void requireActive(Conversation conversation) {
        if (conversation.getStatus() != ConversationStatus.ACTIVE) {
            throw new BusinessRuleException("A conversa está desativada e não aceita novas mensagens.");
        }
    }

    /** Comunidade arquivada bloqueia mensagens e administração dos subgrupos */
    public void requireCommunityActive(Conversation conversation) {
        if (conversation.getCommunityId() == null) return;

        ProjectCommunity community = communities.findById(conversation.getCommunityId())
                .orElseThrow(() -> new ResourceNotFoundException(
                        "Comunidade não encontrada para a conversa: " + conversation.getId()));
        if (community.getStatus() != CommunityStatus.ACTIVE) {
            throw new BusinessRuleException("A comunidade está arquivada.");
        }
    }

    /** Comunidade ativa e user com papel de escrita; o VIEWER só lê */
    public void requireCommunityWriter(Conversation conversation, UUID actor) {
        requireCommunityActive(conversation);
        if (conversation.getCommunityId() == null) return;
        requireWriter(requireCommunity(conversation.getCommunityId()), actor);
    }

    /** Autoriza e executa a edição/exclusão de mensagem dentro do guard */
    public <T> T withMessageMutation(String conversationId, UUID actor, Function<Conversation, T> mutation) {
        try {
            return guard.execute(() -> {
                // participante, conversa humana, ativa e com comunidade ativa
                Conversation current = requireParticipant(conversationId, actor);
                if (current.getConversationType() == ConversationType.CHAT_BOT) {
                    throw new BusinessRuleException("A conversa não permite alterações de mensagens.");
                }
                requireActive(current);
                requireCommunityWriter(current, actor);
                return mutation.apply(current);
            });
        } catch (BusinessException business) {
            throw business;
        } catch (RuntimeException failure) {
            // erro técnico: não dá para saber se a mudança foi gravada
            BusinessException uncertain = new MessageMutationUncertainException();
            uncertain.addSuppressed(failure);
            throw uncertain;
        }
    }
}
