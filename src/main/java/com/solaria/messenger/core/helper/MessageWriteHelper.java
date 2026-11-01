package com.solaria.messenger.core.helper;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataRetrievalFailureException;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

import com.solaria.messenger.core.exception.BusinessException;
import com.solaria.messenger.core.exception.BusinessRuleException;
import com.solaria.messenger.core.exception.ConcurrentUpdateException;
import com.solaria.messenger.core.exception.MessageMutationNotAppliedException;
import com.solaria.messenger.core.exception.MessageMutationUncertainException;
import com.solaria.messenger.core.exception.MessageSendFailedException;
import com.solaria.messenger.core.exception.ResourceNotFoundException;
import com.solaria.messenger.core.model.Conversation;
import com.solaria.messenger.core.model.Message;
import com.solaria.messenger.core.model.enums.ConversationType;
import com.solaria.messenger.core.repository.ConversationRepository;
import com.solaria.messenger.core.repository.MessageRepository;
import com.solaria.messenger.core.security.rbac.RbacAuthorizationService;
import com.solaria.messenger.core.support.ConversationMutationGuard;
import com.solaria.messenger.core.support.ConversationSyncClock;


/**
 * Grava, edita e exclui mensagens, com as regras de envio e de autoria.
 *
 * <p>
 * O envio usa a sequência alocada pelo repository. Edição e exclusão usam o contador {@code mutationEpoch} como CAS: a
 * escrita só vale se o contador ainda for o lido. Toda gravação leva o {@code changeVersion}, tirado do relógio de
 * sincronização antes da escrita e gravado no mesmo insert ou CAS. Falha técnica com resultado incerto vira erro de
 * persistência incerta. Falha ao atualizar a última interação só gera log, porque a mensagem já foi gravada.
 * </p>
 *
 * <p>
 * <b>Depende de:</b>
 * </p>
 * <ul>
 *     <li>{@link MessageRepository} -> insere, edita e exclui mensagens.</li>
 *     <li>{@link ConversationRepository} -> atualiza a última interação.</li>
 *     <li>{@link RbacAuthorizationService} -> identifica o autor e confere a autoria.</li>
 *     <li>{@link ConversationAccessHelper} -> confere participação, conversa e comunidade.</li>
 *     <li>{@link ConversationMutationGuard} -> serializa a operação na JVM.</li>
 *     <li>{@link ConversationSyncClock} -> fornece o {@code changeVersion} de cada gravação.</li>
 * </ul>
 *
 * <p>
 * Principais operações disponibilizadas:
 * </p>
 * <ul>
 *     <li>{@link #appendAndTouch(Conversation, Message)} -> grava a mensagem e atualiza a última interação.</li>
 *     <li>{@link #mutate(String, String, boolean)} -> edita ou exclui uma mensagem própria.</li>
 * </ul>
 */
@Component
public class MessageWriteHelper {

    private static final Logger log = LoggerFactory.getLogger(MessageWriteHelper.class);
    private static final int MAX_DELETE_MUTATION_ATTEMPTS = 3;

    private final MessageRepository messageRepository;
    private final ConversationRepository conversations;
    private final RbacAuthorizationService rbac;
    private final ConversationAccessHelper access;
    private final ConversationMutationGuard guard;
    private final ConversationSyncClock clock;
    private final Duration mutationWindow;

    public MessageWriteHelper(MessageRepository messages, ConversationRepository conversations,
            RbacAuthorizationService rbac, ConversationAccessHelper access, ConversationMutationGuard guard,
            ConversationSyncClock clock, @Value("${app.messages.mutation-window:PT30M}") Duration mutationWindow) {
        this.messageRepository = messages;
        this.conversations = conversations;
        this.rbac = rbac;
        this.access = access;
        this.guard = guard;
        this.clock = clock;
        this.mutationWindow = mutationWindow;
    }

    // envio

    /**
     * Grava a mensagem com a próxima sequência e a versão de sincronização, e atualiza a última interação da conversa.
     *
     * @param conversation conversa da mensagem.
     * @param message mensagem a gravar.
     * @return mensagem gravada, com a sequência e o {@code changeVersion} alocados.
     * @throws MessageSendFailedException caso a sequência não possa ser alocada, a consulta falhe ou o banco rejeite a
     *     escrita. Traz o status e o código do erro.
     */
    public Message appendAndTouch(Conversation conversation, Message message) {
        Message saved = append(message);
        updateInteractionAfterInsert(conversation, saved);
        return saved;
    }

    /**
     * Carimba a versão e insere com a próxima sequência. Cada tipo de falha vira um erro com status e código próprios
     */
    private Message append(Message message) {
        // shortcut: a versão é alocada antes do insert e sem transação, então outro pod pode gravar uma versão maior
        // antes desta; o buraco aparece na sequence da conversa e o cliente busca de novo, evoluir se isso virar rotina
        try {
            message.setChangeVersion(clock.nextVersion());
        } catch (RuntimeException unavailable) {
            throw sendFailure(message, HttpStatus.SERVICE_UNAVAILABLE, "DEPENDENCY_UNAVAILABLE",
                    "Não foi possível obter a versão de sincronização; esta tentativa não inseriu mensagem.", unavailable);
        }

        // só o repository repete em colisão de sequência; aqui nenhuma falha reinicia o envio
        try {
            return messageRepository.insertWithPersistedSequence(message);
        } catch (DataRetrievalFailureException unavailable) {
            throw sendFailure(message, HttpStatus.SERVICE_UNAVAILABLE, "DEPENDENCY_UNAVAILABLE",
                    "Não foi possível consultar a sequência; esta tentativa não inseriu mensagem.", unavailable);
        } catch (OptimisticLockingFailureException exhausted) {
            throw sendFailure(message, HttpStatus.CONFLICT, "CONCURRENT_MODIFICATION",
                    "Não foi possível alocar uma sequência; esta tentativa não inseriu mensagem.", exhausted);
        } catch (DuplicateKeyException rejected) {
            throw sendFailure(message, HttpStatus.CONFLICT, "DATA_INTEGRITY_VIOLATION",
                    "O banco rejeitou a escrita por uma restrição de integridade.", rejected);
        } catch (RuntimeException uncertain) {
            throw sendFailure(message, HttpStatus.SERVICE_UNAVAILABLE, "MESSAGE_PERSISTENCE_UNCERTAIN",
                    "Não foi possível confirmar a persistência. Consulte o histórico antes de reenviar.", uncertain);
        }
    }

    /** Erro de envio com status e código, levando o clientMessageId para o app correlacionar */
    private BusinessException sendFailure(Message message, HttpStatus status, String code,
            String detail, RuntimeException cause) {
        Map<String, Object> correlation = message.getClientMessageId() == null ? Map.of()
                : Map.of("clientMessageId", message.getClientMessageId().toString());
        BusinessException failure = new MessageSendFailedException(detail, status, code, correlation);
        failure.addSuppressed(cause);
        return failure;
    }

    /** A mensagem já foi gravada; se isto falhar, só gera log e a resposta segue normal */
    private void updateInteractionAfterInsert(Conversation conversation, Message saved) {
        try {
            conversation.setLastInteractionAt(saved.getTimestamp());
            conversations.updateLastInteraction(conversation.getId(), saved.getTimestamp());
        } catch (RuntimeException failure) {
            log.warn("Mensagem persistida; falha ao atualizar interação (messageId={}, conversationId={})",
                    saved.getId(), saved.getConversationId(), failure);
        }
    }

    // edição e exclusão

    /**
     * Edita ou exclui uma mensagem do próprio user.
     *
     * <p>
     * A exclusão tenta até três vezes quando o CAS não aplica. A edição tenta uma vez só. Se a mensagem já está no
     * estado pedido, nada é gravado e o resultado traz {@code changed} como falso.
     * </p>
     *
     * @param messageId identificador da mensagem.
     * @param content novo conteúdo. Ignorado na exclusão.
     * @param delete {@code true} para excluir, {@code false} para editar.
     * @return resultado com a mensagem e a indicação de alteração efetiva.
     * @throws ResourceNotFoundException caso a mensagem não exista.
     * @throws BusinessRuleException caso a conversa seja do tipo CHAT_BOT, esteja inativa, a mensagem excluída seja
     *     editada, ou o prazo de {@code app.messages.mutation-window} (30 min por padrão) desde o envio tenha acabado.
     *     Repetir um pedido já aplicado continua valendo fora do prazo.
     * @throws MessageMutationNotAppliedException caso o CAS não aplique em todas as tentativas.
     * @throws MessageMutationUncertainException caso falha técnica deixe incerto se a mudança foi gravada.
     */
    public MessageMutationResult mutate(String messageId, String content, boolean delete) {
        int attempts = delete ? MAX_DELETE_MUTATION_ATTEMPTS : 1;
        for (int attempt = 0; attempt < attempts; attempt++) {
            try {
                return mutateOnce(messageId, content, delete);
            } catch (MessageMutationNotAppliedException notApplied) {
                if (attempt == attempts - 1) throw notApplied;
            }
        }
        throw new IllegalStateException("Message mutation attempts exhausted");
    }

    /** Relê a mensagem, confere permissão e grava com CAS */
    private MessageMutationResult mutateOnce(String messageId, String content, boolean delete) {
        // o user precisa ser o autor da mensagem
        UUID actor = rbac.currentUserId();
        Message initial = requireMessage(messageId);
        rbac.requireOwnResource(initial.getSenderId());

        // withMessageMutation já recusa CHAT_BOT, conversa inativa e comunidade arquivada
        return withMessageMutation(initial.getConversationId(), actor, conversation -> {
            // relê dentro da autorização: a mensagem precisa continuar do user e na mesma conversa
            Message current = requireMessage(messageId);
            rbac.requireOwnResource(current.getSenderId());
            if (!conversation.getId().equals(current.getConversationId())) throw concurrentMutation();
            if (!delete && current.isDeleted()) {
                throw new BusinessRuleException("Uma mensagem excluída não pode ser editada.");
            }

            // já está no estado pedido (excluída ou com o mesmo conteúdo): nada a gravar
            if (delete ? current.isDeleted() : Objects.equals(current.getContent(), content)) {
                return new MessageMutationResult(current, false);
            }

            // só a mudança efetiva é limitada pela janela; repetir um pedido já aplicado continua valendo
            requireInsideMutationWindow(current);

            // grava só se o epoch ainda for o lido, com a versão da mudança no mesmo CAS
            // shortcut: mesma janela do envio (versão alocada antes do CAS, sem transação), mas edição e exclusão não deixam
            // buraco na sequence; só o evento ao vivo cobre a corrida, evoluir junto com o envio
            long epoch = current.getMutationEpoch();
            long nextEpoch = Math.addExact(epoch, 1);
            long version = clock.nextVersion();
            long modified = delete
                    ? messageRepository.deleteIfCurrent(messageId, conversation.getId(), actor, epoch, nextEpoch, version)
                    : messageRepository.editIfCurrent(messageId, conversation.getId(), actor, epoch, content, nextEpoch, version);
            if (modified != 1) throw new MessageMutationNotAppliedException();

            // reflete a gravação no objeto em memória para a resposta
            current.setMutationEpoch(nextEpoch);
            current.setChangeVersion(version);
            applyInMemory(current, content, delete);
            return new MessageMutationResult(current, true);
        });
    }

    /** Autoriza e executa a edição/exclusão de mensagem dentro do guard */
    private <T> T withMessageMutation(String conversationId, UUID actor, Function<Conversation, T> mutation) {
        try {
            return guard.execute(() -> {
                // participante, conversa humana, ativa e com comunidade ativa
                Conversation current = access.requireParticipant(conversationId, actor);
                if (current.getConversationType() == ConversationType.CHAT_BOT) {
                    throw new BusinessRuleException("A conversa não permite alterações de mensagens.");
                }
                access.requireCommunityActive(current);
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

    /** Reflete a edição ou exclusão já gravada no objeto devolvido na resposta */
    private static void applyInMemory(Message message, String content, boolean delete) {
        if (delete) {
            message.setDeleted(true);
            message.setContent(null);
        } else {
            message.setContent(content);
            message.setEdited(true);
        }
    }

    /** Recusa a mudança quando o prazo para editar ou excluir já passou; mensagem sem carimbo de tempo não expira */
    private void requireInsideMutationWindow(Message message) {
        Instant sentAt = message.getTimestamp();
        if (sentAt != null && Instant.now().isAfter(sentAt.plus(mutationWindow))) {
            throw new BusinessRuleException("O prazo para editar ou excluir esta mensagem acabou.");
        }
    }

    private Message requireMessage(String messageId) {
        return messageRepository.findById(messageId)
                .orElseThrow(() -> new ResourceNotFoundException("Mensagem não encontrada: " + messageId));
    }

    /** A mensagem lida não pertence mais à conversa autorizada */
    private BusinessException concurrentMutation() {
        return new ConcurrentUpdateException("A mensagem foi modificada por outra operação concorrente.");
    }
}
