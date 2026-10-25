package com.solaria.messenger.core.service.conversation;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.MongoTransactionManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import com.solaria.messenger.core.exception.BusinessRuleException;
import com.solaria.messenger.core.exception.UnauthorizedAccessException;
import com.solaria.messenger.core.model.Conversation;
import com.solaria.messenger.core.model.ConversationUserState;
import com.solaria.messenger.core.model.Message;
import com.solaria.messenger.core.model.ProjectCommunity;
import com.solaria.messenger.core.model.enums.CommunityStatus;
import com.solaria.messenger.core.model.enums.ConversationStatus;
import com.solaria.messenger.core.model.enums.ConversationType;
import com.solaria.messenger.core.repository.CommunityRepository;
import com.solaria.messenger.core.repository.ConversationRepository;
import com.solaria.messenger.core.repository.ConversationUserStateRepository;
import com.solaria.messenger.core.repository.MessageRepository;
import com.solaria.messenger.core.service.OptimisticMutation;

/**
 * Service que grava a conversa e a entrada de participantes numa transação Mongo
 *
 * <p>
 * A conversa e o estado pessoal de cada participante são salvos juntos: se um falhar, nada é gravado.
 * Não faz chamada HTTP nem publica eventos.
 * </p>
 * <ul>
 *     <li>{@link #createWithInitialStates(Conversation)} -> cria a conversa e o estado de cada participante.</li>
 *     <li>{@link #admitParticipants(String, Long, UUID, Set)} -> adiciona participantes a um grupo.</li>
 * </ul>
 */
@Service
public class ConversationAdmissionService {

    private static final String CONCURRENT = "CONCURRENT_MODIFICATION";

    private final ConversationRepository conversations;
    private final ConversationUserStateRepository userStates;
    private final MessageRepository messages;
    private final CommunityRepository communities;
    private final TransactionTemplate transaction;

    public ConversationAdmissionService(ConversationRepository conversations, ConversationUserStateRepository userStates,
            MessageRepository messages, CommunityRepository communities,
            MongoDatabaseFactory databaseFactory) {
        this.conversations = conversations;
        this.userStates = userStates;
        this.messages = messages;
        this.communities = communities;
        // único ponto com transação no módulo, por isso o gerenciador é criado aqui e não como bean global
        this.transaction = new TransactionTemplate(new MongoTransactionManager(databaseFactory));
    }

    /** Cria a conversa e o estado de cada participante; qualquer falha desfaz tudo */
    public Conversation createWithInitialStates(Conversation conversation) {
        return Objects.requireNonNull(transaction.execute(status -> {
            ConversationType type = conversation.getConversationType();

            // conversa ativa, com o criador e pelo menos dois participantes
            if (conversation.getStatus() != ConversationStatus.ACTIVE || conversation.getParticipantIds().size() < 2
                    || !conversation.hasParticipant(conversation.getCreatedBy())) {
                throw new BusinessRuleException("A criação exige criador e ao menos dois participantes distintos.");
            }

            // grupo: o criador começa como gestor / direta: dois participantes e fora de comunidade
            if (type == ConversationType.GROUP) {
                if (!conversation.getCreatedBy().equals(conversation.getManagerUserId())) {
                    throw new BusinessRuleException("O criador deve ser o gestor inicial.");
                }
            } else if (type != ConversationType.DIRECT
                    || conversation.getParticipantIds().size() != 2 || conversation.getCommunityId() != null) {
                throw new BusinessRuleException("Tipo de conversa inválido para criação com estado pessoal.");
            }

            // a chave da direta são os dois ids ordenados e unidos por "|"
            if (type == ConversationType.DIRECT
                    && !String.join("|", conversation.getParticipantIds().stream().map(UUID::toString).sorted().toList())
                            .equals(conversation.getDirectKey())) {
                throw new BusinessRuleException("A identidade canônica da direta é inválida.");
            }
            requireCommunity(conversation, conversation.getCreatedBy(), conversation.getParticipantIds());

            // salva a conversa e o estado de cada participante a partir da sequência 0
            Conversation saved = conversations.insert(conversation);
            for (UUID user : saved.getParticipantIds()) insertState(saved.getId(), user, 0);
            return saved;
        }));
    }

    /** Adiciona participantes ao grupo sem mexer no cursor e nas preferências de quem já estava */
    public Conversation admitParticipants(String id, Long expectedVersion, UUID actor, Set<UUID> requested) {
        return Objects.requireNonNull(transaction.execute(status -> {
            // a versão lida pelo chamador precisa ser a atual
            Conversation current = conversations.findById(id).orElse(null);
            if (current == null || !Objects.equals(expectedVersion, current.getVersion())) {
                throw new OptimisticLockingFailureException(CONCURRENT);
            }
            requireCanAdmit(current, actor);
            requireCommunity(current, actor, requested);

            // última sequência da conversa: os novos começam a ler a partir dela
            int head = headSequence(id);

            // quem já participa precisa ter estado válido
            for (UUID user : current.getParticipantIds()) requireState(id, user, head, false);

            // junta os novos; se ninguém novo entrou, não grava nada
            Set<UUID> participants = new LinkedHashSet<>(current.getParticipantIds());
            participants.addAll(requested);
            if (participants.equals(current.getParticipantIds())) return current;

            // grava os participantes com CAS e cria o estado dos novos
            if (conversations.updateParticipantsIfCurrent(id, current.getVersion(), participants,
                    current.getManagerUserId(), Instant.now(), OptimisticMutation.nextVersion(current.getVersion())) != 1) {
                throw new OptimisticLockingFailureException(CONCURRENT);
            }
            requested.stream().filter(user -> !current.hasParticipant(user)).forEach(user -> requireState(id, user, head, true));
            return conversations.findById(id).orElseThrow();
        }));
    }

    /** Só grupo ativo, e só o gestor atual adiciona */
    private void requireCanAdmit(Conversation current, UUID actor) {
        if (current.getConversationType() != ConversationType.GROUP || current.getStatus() != ConversationStatus.ACTIVE) {
            throw new BusinessRuleException("O grupo não aceita novas admissões.");
        }
        if (!current.hasParticipant(actor) || !actor.equals(current.getManagerUserId())) {
            throw new UnauthorizedAccessException("Somente o gestor atual pode admitir participantes.");
        }
    }

    /** Sequência da última mensagem da conversa; 0 quando ainda não há mensagens */
    private int headSequence(String conversationId) {
        Message latest = messages.findFirstByConversationIdOrderBySequenceDesc(conversationId).orElse(null);
        if (latest == null) return 0;
        if (latest.getSequence() < 1) throw new BusinessRuleException("Sequência persistida inválida.");
        return latest.getSequence();
    }

    /** Se a conversa é de comunidade, ela precisa estar ativa e ter o ator e todos os participantes como membros */
    private void requireCommunity(Conversation conversation, UUID actor, Set<UUID> participants) {
        if (conversation.getCommunityId() == null) return;

        ProjectCommunity parent = communities.findById(conversation.getCommunityId()).orElse(null);
        if (parent == null || !parent.isMember(actor)) {
            throw new UnauthorizedAccessException("O criador ou gestor não participa da comunidade.");
        }
        if (parent.getStatus() != CommunityStatus.ACTIVE) throw new BusinessRuleException("A comunidade está arquivada.");
        if (participants.stream().anyMatch(user -> !parent.isMember(user))) {
            throw new BusinessRuleException("A participação na comunidade mudou antes da admissão.");
        }
    }

    /** Cria o estado de quem está entrando ou confere o estado de quem já participa */
    private void requireState(String conversationId, UUID user, int head, boolean newMembership) {
        ConversationUserState state = userStates.findByConversationIdAndUserId(conversationId, user).orElse(null);
        if (state == null && newMembership) {
            insertState(conversationId, user, head);
        } else if (state == null || !state.hasValidBaseline() || state.getLastReadSequence() > head) {
            throw new BusinessRuleException("Estado pessoal indisponível: baseline de admissão não verificável.");
        }
    }

    /** Grava o estado inicial com baseline e cursor de leitura na mesma sequência, sem mute nem archive */
    private void insertState(String conversationId, UUID user, int baseline) {
        ConversationUserState state = new ConversationUserState();
        state.setConversationId(conversationId);
        state.setUserId(user);
        state.setBaselineSequence(baseline);
        state.setLastReadSequence(baseline);
        state.setMuted(false);
        state.setArchived(false);
        userStates.insert(state);
    }
}
