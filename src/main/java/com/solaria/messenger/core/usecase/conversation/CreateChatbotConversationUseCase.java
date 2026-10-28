package com.solaria.messenger.core.usecase.conversation;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.solaria.messenger.core.contract.request.ChatbotConversationRequestDTO;
import com.solaria.messenger.core.contract.response.ConversationResponseDTO;
import com.solaria.messenger.core.helper.NotificationHelper;
import com.solaria.messenger.core.model.Conversation;
import com.solaria.messenger.core.model.enums.ConversationType;
import com.solaria.messenger.core.repository.ConversationRepository;
import com.solaria.messenger.core.security.rbac.RbacAuthorizationService;

import static com.solaria.messenger.core.helper.PureRulesHelper.newConversation;
import static com.solaria.messenger.core.mapper.ConversationMapper.toResponse;

/**
 * Caso de uso que cria a conversa do user com o chatbot.
 *
 * <p>
 * Cria uma conversa do tipo {@code CHAT_BOT} com o user atual como único participante. A conversa guarda o tipo de
 * usuário e os detalhes enviados no pedido. Ela não tem estado pessoal de leitura.
 * </p>
 *
 * <p>
 * <b>Depende de:</b>
 * </p>
 * <ul>
 *     <li>{@link ConversationRepository} -> grava a conversa.</li>
 *     <li>{@link RbacAuthorizationService} -> identifica o user atual.</li>
 *     <li>{@link NotificationHelper} -> avisa os clientes que a conversa mudou.</li>
 * </ul>
 *
 * <p>
 * Principal operação disponibilizada:
 * </p>
 * <ul>
 *     <li>{@link #execute(ChatbotConversationRequestDTO)} -> cria a conversa com o chatbot.</li>
 * </ul>
 */
@Service
public class CreateChatbotConversationUseCase {

    private final ConversationRepository conversationRepository;
    private final RbacAuthorizationService rbac;
    private final NotificationHelper notifications;

    public CreateChatbotConversationUseCase(ConversationRepository conversations, RbacAuthorizationService rbac,
            NotificationHelper notifications) {
        this.conversationRepository = conversations;
        this.rbac = rbac;
        this.notifications = notifications;
    }

    /**
     * Cria a conversa do user com o chatbot.
     *
     * @param dto tipo de usuário e detalhes da conversa.
     * @return conversa criada, sem estado pessoal.
     */
    public ConversationResponseDTO execute(ChatbotConversationRequestDTO dto) {
        UUID currentUserId = rbac.currentUserId();

        Conversation conversation = newConversation(ConversationType.CHAT_BOT, currentUserId,
                new LinkedHashSet<>(List.of(currentUserId)));
        conversation.setUserType(dto.getUserType());
        conversation.setUserDetails(dto.getUserDetails());

        Conversation saved = conversationRepository.save(conversation);
        notifications.publishConversationChanged(saved.getId());
        return toResponse(saved);
    }
}
