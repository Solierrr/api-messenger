package com.solaria.messenger.core.helper;

import java.time.Instant;
import java.util.UUID;

import org.springframework.stereotype.Component;

import com.solaria.messenger.core.contract.request.AttachmentRequestDTO;
import com.solaria.messenger.core.exception.BusinessException;
import com.solaria.messenger.core.exception.DependencyUnavailableException;
import com.solaria.messenger.core.exception.InvalidFieldException;
import com.solaria.messenger.core.exception.MediaMismatchException;
import com.solaria.messenger.core.exception.MediaNotUploadedException;
import com.solaria.messenger.core.integration.storage.MediaProperties;
import com.solaria.messenger.core.model.Attachment;
import com.solaria.messenger.core.model.StoredMedia;
import com.solaria.messenger.core.model.enums.AttachmentType;
import com.solaria.messenger.core.port.MediaStore;

/**
 * Valida o anexo de uma mensagem enviada e monta o que fica gravado na mensagem.
 *
 * <p>
 * Localização só precisa das duas coordenadas. Arquivo (imagem, vídeo, áudio, documento) só vale se já foi depositado
 * e se o que está guardado confere com o que a mensagem declara: mesma conversa, mesmo remetente, mesmo tamanho e mesmo
 * tipo. O prazo do anexo conta da criação do arquivo. Campos que não fazem sentido para o tipo (duração de imagem, nome
 * de áudio) não são gravados.
 * </p>
 * <p>
 * <b>Depende de:</b>
 * </p>
 * <ul>
 *     <li>{@link MediaStore} -> consulta o arquivo depositado.</li>
 *     <li>{@link MediaRulesHelper} -> tipo, mime e tamanho permitidos.</li>
 *     <li>{@link MediaProperties} -> prazo de retenção.</li>
 * </ul>
 * <p>
 * Principais operações disponibilizadas:
 * </p>
 * <ul>
 *     <li>{@link #build(String, UUID, AttachmentRequestDTO)} -> valida o anexo e devolve a descrição a gravar.</li>
 * </ul>
 */
@Component
public class AttachmentHelper {

    private final MediaStore store;
    private final MediaRulesHelper rules;
    private final MediaProperties properties;

    public AttachmentHelper(MediaStore store, MediaRulesHelper rules, MediaProperties properties) {
        this.store = store;
        this.rules = rules;
        this.properties = properties;
    }

    /**
     * Valida o anexo e monta a descrição gravada na mensagem.
     *
     * @param conversationId conversa em que a mensagem é enviada.
     * @param sender remetente da mensagem.
     * @param request anexo pedido pelo cliente.
     * @return descrição do anexo, com o prazo em arquivos.
     * @throws InvalidFieldException caso falte campo obrigatório do tipo ou o arquivo declarado viole as regras.
     * @throws MediaNotUploadedException caso o arquivo não esteja no armazenamento.
     * @throws MediaMismatchException caso o arquivo guardado não confira com o declarado.
     * @throws DependencyUnavailableException caso o armazenamento não responda.
     */
    public Attachment build(String conversationId, UUID sender, AttachmentRequestDTO request) {
        AttachmentType type = request.getType();
        if (type == null) throw new InvalidFieldException("attachment.type é obrigatório.");
        return type.hasFile() ? buildFile(conversationId, sender, request) : buildLocation(request);
    }

    private Attachment buildLocation(AttachmentRequestDTO request) {
        if (request.getLatitude() == null || request.getLongitude() == null) {
            throw new InvalidFieldException("Uma localização exige latitude e longitude.");
        }
        Attachment attachment = new Attachment();
        attachment.setType(AttachmentType.LOCATION);
        attachment.setLatitude(request.getLatitude());
        attachment.setLongitude(request.getLongitude());
        attachment.setLabel(request.getLabel());
        return attachment;
    }

    private Attachment buildFile(String conversationId, UUID sender, AttachmentRequestDTO request) {
        AttachmentType type = request.getType();
        if (request.getMediaId() == null || request.getMediaId().isBlank()) {
            throw new InvalidFieldException("attachment.mediaId é obrigatório para " + type + ".");
        }
        if (request.getMimeType() == null || request.getMimeType().isBlank()) {
            throw new InvalidFieldException("attachment.mimeType é obrigatório para " + type + ".");
        }
        if (request.getSizeBytes() == null) {
            throw new InvalidFieldException("attachment.sizeBytes é obrigatório para " + type + ".");
        }
        rules.requireAllowed(type, request.getMimeType(), request.getSizeBytes());
        String mime = MediaRulesHelper.normalizeMime(request.getMimeType());

        StoredMedia stored = find(request.getMediaId());
        if (!Instant.now().isBefore(stored.createdAt().plus(properties.ttl()))) throw new MediaNotUploadedException();
        if (!conversationId.equals(stored.conversationId())) {
            throw new MediaMismatchException("O arquivo foi depositado em outra conversa.");
        }
        if (!sender.equals(stored.uploader())) {
            throw new MediaMismatchException("O arquivo foi depositado por outro usuário.");
        }
        if (stored.sizeBytes() != request.getSizeBytes()) {
            throw new MediaMismatchException("O tamanho declarado é diferente do arquivo depositado.");
        }
        if (!mime.equals(MediaRulesHelper.normalizeMime(stored.mimeType()))) {
            throw new MediaMismatchException("O tipo declarado é diferente do arquivo depositado.");
        }

        Attachment attachment = new Attachment();
        attachment.setType(type);
        attachment.setMediaId(request.getMediaId());
        attachment.setMimeType(mime);
        attachment.setSizeBytes(stored.sizeBytes());
        attachment.setClientPreview(request.getClientPreview());
        attachment.setExpiresAt(stored.createdAt().plus(properties.ttl()));
        if (type == AttachmentType.AUDIO || type == AttachmentType.VIDEO) attachment.setDurationMs(request.getDurationMs());
        if (type == AttachmentType.IMAGE || type == AttachmentType.VIDEO) {
            attachment.setWidth(request.getWidth());
            attachment.setHeight(request.getHeight());
        }
        if (type == AttachmentType.FILE) attachment.setName(request.getName());
        return attachment;
    }

    private StoredMedia find(String mediaId) {
        try {
            return store.stat(mediaId).orElseThrow(MediaNotUploadedException::new);
        } catch (BusinessException business) {
            throw business;
        } catch (RuntimeException failure) {
            throw new DependencyUnavailableException("O armazenamento de mídia não respondeu.", failure);
        }
    }
}
