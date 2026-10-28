package com.solaria.messenger.core.helper;

import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.function.Function;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataRetrievalFailureException;
import org.springframework.dao.DuplicateKeyException;
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

import static com.solaria.messenger.core.helper.PureRulesHelper.requireConversationActive;

@Component
public class MessageWriteHelper {

    private static final Logger log = LoggerFactory.getLogger(MessageWriteHelper.class);
    private static final int MAX_DELETE_MUTATION_ATTEMPTS = 3;

    private final MessageRepository messageRepository;
    private final ConversationRepository conversations;
    private final RbacAuthorizationService rbac;
    private final ConversationAccessHelper access;
    private final ConversationMutationGuard guard;

    public MessageWriteHelper(MessageRepository messages, ConversationRepository conversations,
            RbacAuthorizationService rbac, ConversationAccessHelper access, ConversationMutationGuard guard) {
        this.messageRepository = messages;
        this.conversations = conversations;
        this.rbac = rbac;
        this.access = access;
        this.guard = guard;
    }

    // envio

    /** Grava a mensagem e atualiza a última interação da conversa */
    public Message appendAndTouch(Conversation conversation, Message message) {
        Message saved = append(message);
        updateInteractionAfterInsert(conversation, saved);
        return saved;
    }

    /** Insere com a próxima sequência; cada tipo de falha vira um erro com status e código próprios */
    private Message append(Message message) {
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

    /** Exclusão tenta até 3 vezes quando o CAS não grava; edição tenta só uma */
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

            // grava só se o epoch ainda for o lido
            long epoch = current.getMutationEpoch();
            long nextEpoch = Math.addExact(epoch, 1);
            long modified = delete
                    ? messageRepository.deleteIfCurrent(messageId, conversation.getId(), actor, epoch, nextEpoch)
                    : messageRepository.editIfCurrent(messageId, conversation.getId(), actor, epoch, content, nextEpoch);
            if (modified != 1) throw new MessageMutationNotAppliedException();

            // reflete a gravação no objeto em memória para a resposta
            current.setMutationEpoch(nextEpoch);
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
                requireConversationActive(current);
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

    private Message requireMessage(String messageId) {
        return messageRepository.findById(messageId)
                .orElseThrow(() -> new ResourceNotFoundException("Mensagem não encontrada: " + messageId));
    }

    /** A mensagem lida não pertence mais à conversa autorizada */
    private BusinessException concurrentMutation() {
        return new ConcurrentUpdateException("A mensagem foi modificada por outra operação concorrente.");
    }
}
