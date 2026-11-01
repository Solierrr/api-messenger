package com.solaria.messenger.core.helper;

import java.time.Instant;
import java.util.LinkedHashSet;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.data.mongodb.MongoDatabaseFactory;
import org.springframework.data.mongodb.MongoTransactionManager;
import org.springframework.stereotype.Component;
import org.springframework.transaction.TransactionException;
import org.springframework.transaction.support.TransactionTemplate;

import com.solaria.messenger.core.exception.BusinessException;
import com.solaria.messenger.core.exception.BusinessRuleException;
import com.solaria.messenger.core.exception.PersistenceUncertainException;
import com.solaria.messenger.core.exception.UnauthorizedAccessException;
import com.solaria.messenger.core.model.Conversation;
import com.solaria.messenger.core.model.ConversationUserState;
import com.solaria.messenger.core.model.Message;
import com.solaria.messenger.core.model.ProjectCommunity;
import com.solaria.messenger.core.model.enums.ConversationType;
import com.solaria.messenger.core.repository.CommunityRepository;
import com.solaria.messenger.core.repository.ConversationRepository;
import com.solaria.messenger.core.repository.ConversationUserStateRepository;
import com.solaria.messenger.core.repository.MessageRepository;

import static com.solaria.messenger.core.helper.PureRulesHelper.directKey;
import static com.solaria.messenger.core.helper.PureRulesHelper.nextVersion;
import static com.solaria.messenger.core.helper.PureRulesHelper.requireCommunityActive;

/**
 * Grava conversas e admissões de participantes junto com os estados pessoais.
 *
 * <p>
 * É o único ponto com transação no módulo. A transação é criada aqui, e não como bean global. Conversa e estados são
 * gravados juntos: se qualquer parte falha, tudo é desfeito. Erros de negócio e de concorrência passam como estão.
 * Falha técnica com resultado incerto vira erro de persistência incerta.
 * </p>
 *
 * <p>
 * <b>Depende de:</b>
 * </p>
 * <ul>
 *     <li>{@link ConversationRepository} -> grava conversas e atualiza participantes com CAS.</li>
 *     <li>{@link ConversationUserStateRepository} -> grava os estados pessoais.</li>
 *     <li>{@link MessageRepository} -> consulta a última sequência da conversa.</li>
 *     <li>{@link CommunityRepository} -> confere a comunidade do subgrupo.</li>
 *     <li>{@link MongoDatabaseFactory} -> base para o gerenciador de transação.</li>
 * </ul>
 *
 * <p>
 * Principais operações disponibilizadas:
 * </p>
 * <ul>
 *     <li>{@link #persistNew(Conversation)} -> grava uma conversa nova e traduz as falhas.</li>
 *     <li>{@link #createWithInitialStates(Conversation)} -> cria a conversa e os estados iniciais na transação.</li>
 *     <li>{@link #admitParticipants(String, Long, UUID, Set)} -> adiciona participantes a um grupo existente.</li>
 * </ul>
 */
@Component
public class ConversationAdmissionHelper {

    private static final String CONCURRENT = "CONCURRENT_MODIFICATION";

    private final ConversationRepository conversations;
    private final ConversationUserStateRepository userStates;
    private final MessageRepository messages;
    private final CommunityRepository communities;
    private final TransactionTemplate transaction;

    public ConversationAdmissionHelper(ConversationRepository conversations, ConversationUserStateRepository userStates,
            MessageRepository messages, CommunityRepository communities,
            MongoDatabaseFactory databaseFactory) {
        this.conversations = conversations;
        this.userStates = userStates;
        this.messages = messages;
        this.communities = communities;
        // único ponto com transação no módulo, por isso o gerenciador é criado aqui e não como bean global
        this.transaction = new TransactionTemplate(new MongoTransactionManager(databaseFactory));
    }

    /**
     * Grava uma conversa nova, com estados iniciais, e traduz as falhas.
     *
     * <p>
     * Erro de negócio, chave duplicada e conflito de versão são repassados sem mudança. Erro de transação vira
     * persistência incerta. Qualquer outra falha técnica vira indisponibilidade de dependência.
     * </p>
     *
     * @param conversation conversa a criar.
     * @return conversa gravada.
     * @throws PersistenceUncertainException caso a transação falhe e o resultado não seja conhecido.
     */
    public Conversation persistNew(Conversation conversation) {
        try {
            return createWithInitialStates(conversation);
        } catch (BusinessException | DuplicateKeyException | OptimisticLockingFailureException rejected) {
            throw rejected;
        } catch (TransactionException uncertain) {
            throw new PersistenceUncertainException("CONVERSATION_PERSISTENCE_UNCERTAIN", conversation.getId(), uncertain);
        } catch (RuntimeException failure) {
            throw new PersistenceUncertainException("DEPENDENCY_UNAVAILABLE", conversation.getId(), failure);
        }
    }

    /**
     * Cria a conversa e o estado inicial de cada participante, na mesma transação.
     *
     * <p>
     * Exige conversa ativa, com o criador e pelo menos dois participantes distintos. Grupo começa com o criador como
     * gestor. Direta precisa ter exatamente dois participantes, fora de comunidade, com a chave canônica do par. Em
     * comunidade, o criador e os participantes precisam ser membros. Cada participante começa com a sequência zero.
     * </p>
     *
     * @param conversation conversa a criar.
     * @return conversa gravada.
     * @throws BusinessRuleException caso a conversa não atenda às regras de criação.
     * @throws UnauthorizedAccessException caso o criador não participe da comunidade.
     */
    public Conversation createWithInitialStates(Conversation conversation) {
        return Objects.requireNonNull(transaction.execute(status -> {
            ConversationType type = conversation.getConversationType();

            // com o criador e pelo menos dois participantes
            if (conversation.getParticipantIds().size() < 2
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
            if (type == ConversationType.DIRECT && !directKey(conversation.getParticipantIds()).equals(conversation.getDirectKey())) {
                throw new BusinessRuleException("A identidade canônica da direta é inválida.");
            }
            requireCommunity(conversation, conversation.getCreatedBy(), conversation.getParticipantIds());

            // salva a conversa e o estado de cada participante a partir da sequência 0
            Conversation saved = conversations.insert(conversation);
            for (UUID user : saved.getParticipantIds()) insertState(saved.getId(), user, 0);
            return saved;
        }));
    }

    /**
     * Adiciona participantes a um grupo, dentro de uma transação.
     *
     * <p>
     * A versão lida pelo chamador precisa ser a atual. Quem já participa precisa ter estado válido. Os novos ganham
     * estado a partir da última sequência, sem mexer no cursor nem nas preferências de quem já estava. Quem volta ao
     * grupo reaproveita o estado antigo e perde a marca de saída. Se ninguém novo entrar, nada é gravado.
     * </p>
     *
     * @param id identificador do grupo.
     * @param expectedVersion versão lida pelo chamador.
     * @param actor identificador de quem adiciona. Precisa ser o gestor atual.
     * @param requested participantes a adicionar.
     * @return grupo depois da admissão.
     * @throws OptimisticLockingFailureException caso a versão tenha mudado ou o CAS não aplique.
     * @throws BusinessRuleException caso o grupo não aceite admissões ou a comunidade mude.
     * @throws UnauthorizedAccessException caso o ator não seja o gestor atual.
     */
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
                    current.getManagerUserId(), Instant.now(), nextVersion(current.getVersion())) != 1) {
                throw new OptimisticLockingFailureException(CONCURRENT);
            }
            requested.stream().filter(user -> !current.hasParticipant(user)).forEach(user -> requireState(id, user, head, true));
            return conversations.findById(id).orElseThrow();
        }));
    }

    /** Só grupo ativo, e só o gestor atual adiciona */
    private void requireCanAdmit(Conversation current, UUID actor) {
        if (current.getConversationType() != ConversationType.GROUP) {
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
        requireCommunityActive(parent);
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
        } else if (newMembership) {
            // readmissão: o estado antigo é reaproveitado, então a marca de saída sai junto com a perda de acesso
            userStates.clearLeft(conversationId, user);
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
