package com.solaria.messenger.core.usecase.media;

import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.solaria.messenger.core.exception.BusinessException;
import com.solaria.messenger.core.exception.DependencyUnavailableException;
import com.solaria.messenger.core.exception.MediaExpiredException;
import com.solaria.messenger.core.helper.ConversationAccessHelper;
import com.solaria.messenger.core.integration.storage.MediaProperties;
import com.solaria.messenger.core.model.StoredMedia;
import com.solaria.messenger.core.port.MediaStore;
import com.solaria.messenger.core.security.rbac.RbacAuthorizationService;

/**
 * Caso de uso que dá a um participante o link para retirar um arquivo.
 *
 * <p>
 * O prazo conta da criação do arquivo e é conferido aqui, não pelo bucket, que só limpa depois. Quem saiu da conversa
 * perde a retirada. Ler continua permitido com a comunidade arquivada. O link nasce só agora, porque dura minutos.
 * </p>
 * <p>
 * <b>Depende de:</b>
 * </p>
 * <ul>
 *     <li>{@link MediaStore} -> consulta o arquivo e emite o link de retirada.</li>
 *     <li>{@link ConversationAccessHelper} -> confere a participação na conversa do arquivo.</li>
 *     <li>{@link MediaProperties} -> prazo de retenção.</li>
 * </ul>
 */
@Service
public class GetMediaContentUseCase {

    private final RbacAuthorizationService rbac;
    private final ConversationAccessHelper access;
    private final MediaStore store;
    private final MediaProperties properties;

    public GetMediaContentUseCase(RbacAuthorizationService rbac, ConversationAccessHelper access, MediaStore store,
            MediaProperties properties) {
        this.rbac = rbac;
        this.access = access;
        this.store = store;
        this.properties = properties;
    }

    /**
     * Devolve o link temporário para baixar o arquivo.
     *
     * @param mediaId identificador do arquivo.
     * @return link assinado do armazenamento.
     * @throws MediaExpiredException caso o arquivo não exista mais ou tenha passado do prazo.
     * @throws DependencyUnavailableException caso o armazenamento não responda.
     */
    public String execute(String mediaId) {
        UUID actor = rbac.currentUserId();
        try {
            StoredMedia media = store.stat(mediaId).orElseThrow(MediaExpiredException::new);
            if (!Instant.now().isBefore(media.createdAt().plus(properties.ttl()))) throw new MediaExpiredException();
            access.requireParticipant(media.conversationId(), actor);
            return store.createDownload(mediaId).url();
        } catch (BusinessException business) {
            throw business;
        } catch (RuntimeException failure) {
            throw new DependencyUnavailableException("O armazenamento de mídia não respondeu.", failure);
        }
    }
}
