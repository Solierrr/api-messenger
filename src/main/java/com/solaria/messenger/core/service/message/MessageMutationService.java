package com.solaria.messenger.core.service.message;

import java.util.Objects;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.solaria.messenger.core.contract.request.MessageEditRequestDTO;
import com.solaria.messenger.core.contract.response.MessageResponseDTO;
import com.solaria.messenger.core.exception.BusinessException;
import com.solaria.messenger.core.exception.BusinessRuleException;
import com.solaria.messenger.core.exception.ConcurrentUpdateException;
import com.solaria.messenger.core.exception.InvalidFieldException;
import com.solaria.messenger.core.exception.MessageMutationNotAppliedException;
import com.solaria.messenger.core.exception.ResourceNotFoundException;
import com.solaria.messenger.core.model.Message;
import com.solaria.messenger.core.repository.MessageRepository;
import com.solaria.messenger.core.security.rbac.RbacAuthorizationService;
import com.solaria.messenger.core.service.ConversationAuthorizationService;

import static com.solaria.messenger.core.mapper.MessageMapper.toResponse;

/**
 * Service de edição e exclusão de mensagens
 *
 * <p>
 * O user só mexe nas próprias mensagens. A escrita usa CAS pelo {@code mutationEpoch} da mensagem;
 * mensagem excluída vira tombstone (sem conteúdo) e não volta mais.
 * </p>
 * <ul>
 *     <li>{@link #editMessage(String, MessageEditRequestDTO)} -> troca o conteúdo da mensagem.</li>
 *     <li>{@link #deleteMessage(String)} -> exclui a mensagem.</li>
 * </ul>
 */
@Service
public class MessageMutationService {

    private static final int MAX_DELETE_MUTATION_ATTEMPTS = 3;
    private final MessageRepository messageRepository;
    private final RbacAuthorizationService rbac;
    private final MessageChangeNotifier messageChangeNotifier;
    private final ConversationAuthorizationService resourceAccess;

    public MessageMutationService(MessageRepository messages, RbacAuthorizationService rbac,
            MessageChangeNotifier notifier, ConversationAuthorizationService authorization) {
        this.messageRepository = messages;
        this.rbac = rbac;
        this.messageChangeNotifier = notifier;
        this.resourceAccess = authorization;
    }

    /** Troca o conteúdo de uma mensagem própria que ainda não foi excluída */
    public MessageResponseDTO editMessage(String messageId, MessageEditRequestDTO dto) {
        if (dto == null || dto.getContent() == null || dto.getContent().isBlank() || dto.getContent().length() > 8000) {
            throw new InvalidFieldException("content deve conter entre 1 e 8000 caracteres e não pode ser branco.");
        }
        return mutateMessage(messageId, dto.getContent(), false);
    }

    /** Exclui uma mensagem própria; excluir de novo não grava nem avisa outra vez */
    public MessageResponseDTO deleteMessage(String messageId) {
        return mutateMessage(messageId, null, true);
    }

    /** Aplica a mudança e só avisa os participantes se algo realmente mudou */
    private MessageResponseDTO mutateMessage(String messageId, String content, boolean delete) {
        MessageMutationResult result = mutateMessageWithRevalidation(messageId, content, delete);
        if (result.changed()) {
            if (delete) {
                messageChangeNotifier.notifyDeleted(result.message());
            } else {
                messageChangeNotifier.notifyEdited(result.message());
            }
        }
        return toResponse(result.message());
    }

    /** Exclusão tenta até 3 vezes quando o CAS não grava; edição tenta só uma */
    private MessageMutationResult mutateMessageWithRevalidation(String messageId, String content, boolean delete) {
        int attempts = delete ? MAX_DELETE_MUTATION_ATTEMPTS : 1;
        for (int attempt = 0; attempt < attempts; attempt++) {
            try {
                return mutateMessageOnce(messageId, content, delete);
            } catch (MessageMutationNotAppliedException notApplied) {
                if (attempt == attempts - 1) throw notApplied;
            }
        }
        throw new IllegalStateException("Message mutation attempts exhausted");
    }

    /** Relê a mensagem, confere permissão e grava com CAS */
    private MessageMutationResult mutateMessageOnce(String messageId, String content, boolean delete) {
        // o user precisa ser o autor da mensagem
        UUID actor = rbac.currentUserId();
        Message initial = requireMessage(messageId);
        rbac.requireOwnResource(initial.getSenderId());

        // withMessageMutation já recusa CHAT_BOT, conversa inativa e comunidade arquivada
        return resourceAccess.withMessageMutation(initial.getConversationId(), actor, conversation -> {
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
            if (delete) {
                current.setDeleted(true);
                current.setContent(null);
            } else {
                current.setContent(content);
                current.setEdited(true);
            }
            return new MessageMutationResult(current, true);
        });
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
