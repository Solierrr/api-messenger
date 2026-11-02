package com.solaria.messenger.core.usecase.media;

import java.util.UUID;

import org.springframework.stereotype.Service;

import com.solaria.messenger.core.contract.request.MediaUploadRequestDTO;
import com.solaria.messenger.core.contract.response.MediaUploadResponseDTO;
import com.solaria.messenger.core.exception.BusinessException;
import com.solaria.messenger.core.exception.BusinessRuleException;
import com.solaria.messenger.core.exception.DependencyUnavailableException;
import com.solaria.messenger.core.helper.ConversationAccessHelper;
import com.solaria.messenger.core.helper.MediaRulesHelper;
import com.solaria.messenger.core.model.MediaTicket;
import com.solaria.messenger.core.model.enums.ConversationType;
import com.solaria.messenger.core.port.MediaStore;
import com.solaria.messenger.core.security.rbac.RbacAuthorizationService;

/**
 * Caso de uso que dá a um participante a permissão de depositar um arquivo antes de enviar a mensagem.
 *
 * <p>
 * Quem pode depositar é quem pode enviar mensagem na conversa: participante, em conversa que aceita anexo e com a
 * comunidade ativa. O servidor não guarda nada além do que o próprio armazenamento guarda no arquivo.
 * </p>
 * <p>
 * <b>Depende de:</b>
 * </p>
 * <ul>
 *     <li>{@link ConversationAccessHelper} -> confere a participação e a comunidade ativa.</li>
 *     <li>{@link MediaRulesHelper} -> confere tipo, mime e tamanho.</li>
 *     <li>{@link MediaStore} -> emite a permissão de envio.</li>
 * </ul>
 */
@Service
public class CreateMediaUploadUseCase {

    private final RbacAuthorizationService rbac;
    private final ConversationAccessHelper access;
    private final MediaRulesHelper rules;
    private final MediaStore store;

    public CreateMediaUploadUseCase(RbacAuthorizationService rbac, ConversationAccessHelper access,
            MediaRulesHelper rules, MediaStore store) {
        this.rbac = rbac;
        this.access = access;
        this.rules = rules;
        this.store = store;
    }

    /**
     * Emite a permissão de envio de um arquivo.
     *
     * @param dto conversa, categoria, tipo e tamanho do arquivo.
     * @return identificador do arquivo e destino do envio.
     * @throws BusinessRuleException caso a conversa seja com o assistente ou a comunidade esteja arquivada.
     * @throws DependencyUnavailableException caso o armazenamento não responda.
     */
    public MediaUploadResponseDTO execute(MediaUploadRequestDTO dto) {
        UUID actor = rbac.currentUserId();
        return access.withAccess(dto.getConversationId(), actor, conversation -> {
            if (conversation.getConversationType() == ConversationType.CHAT_BOT) {
                throw new BusinessRuleException("A conversa com o assistente não aceita anexos.");
            }
            access.requireCommunityActive(conversation);
            rules.requireAllowed(dto.getType(), dto.getMimeType(), dto.getSizeBytes());
            String mediaId = UUID.randomUUID().toString();
            MediaTicket ticket;
            try {
                ticket = store.createUpload(mediaId, conversation.getId(), actor,
                        MediaRulesHelper.normalizeMime(dto.getMimeType()), dto.getSizeBytes());
            } catch (BusinessException business) {
                throw business;
            } catch (RuntimeException failure) {
                throw new DependencyUnavailableException("O armazenamento de mídia não respondeu.", failure);
            }
            return new MediaUploadResponseDTO(mediaId,
                    new MediaUploadResponseDTO.Target(ticket.url(), ticket.method(), ticket.headers()),
                    ticket.expiresAt(), rules.maxBytes(dto.getType()));
        });
    }
}
