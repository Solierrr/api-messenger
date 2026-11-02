package com.solaria.messenger.core.port;

import java.util.Optional;
import java.util.UUID;

import com.solaria.messenger.core.model.MediaTicket;
import com.solaria.messenger.core.model.StoredMedia;

/**
 * Porta de saída para o armazenamento temporário da mídia. O servidor não toca nos bytes: só emite permissões para o
 * aparelho falar direto com o armazenamento, e consulta o que está guardado.
 */
public interface MediaStore {

    /**
     * Permissão para o aparelho enviar o arquivo. O tipo, o tamanho e o dono entram na assinatura, para o próprio
     * armazenamento recusar um envio diferente do declarado.
     */
    MediaTicket createUpload(String mediaId, String conversationId, UUID uploader, String mimeType, long sizeBytes);

    /** Link de retirada de curta duração; criado só na hora de baixar. */
    MediaTicket createDownload(String mediaId);

    /** Dados do arquivo guardado, ou vazio se ele nunca foi enviado ou já foi apagado. */
    Optional<StoredMedia> stat(String mediaId);

    /** Apaga o arquivo; não é erro ele já não existir. */
    void delete(String mediaId);
}
