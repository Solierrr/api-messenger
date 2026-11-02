package com.solaria.messenger.core.integration.storage;

import java.net.URL;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.BlobInfo;
import com.google.cloud.storage.HttpMethod;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import com.solaria.messenger.core.exception.DependencyUnavailableException;
import com.solaria.messenger.core.model.MediaTicket;
import com.solaria.messenger.core.model.StoredMedia;
import com.solaria.messenger.core.port.MediaStore;

/**
 * Armazenamento temporário da mídia no Google Cloud Storage.
 *
 * <p>
 * O servidor não toca nos bytes: emite links assinados (V4) para o aparelho enviar e retirar direto do bucket. No envio,
 * o tipo, o tamanho exato e o dono do arquivo entram na assinatura, então o próprio bucket recusa um envio diferente do
 * declarado. O arquivo fica em {@code media/{mediaId}} e guarda a conversa e o dono nos metadados; o servidor não tem
 * tabela de mídia. Apagar no fim do prazo é regra de ciclo de vida do bucket.
 * </p>
 * <p>
 * Sem {@code app.media.bucket}, toda operação responde indisponível e o resto do serviço segue. A credencial vem do
 * ambiente (Workload Identity no cluster) e é buscada só no primeiro uso.
 * </p>
 * <p>
 * Principais operações disponibilizadas:
 * </p>
 * <ul>
 *     <li>{@link #createUpload(String, String, UUID, String, long)} -> link assinado de envio.</li>
 *     <li>{@link #createDownload(String)} -> link assinado de retirada, que força o download.</li>
 *     <li>{@link #stat(String)} -> dono, conversa, tipo, tamanho e criação do arquivo guardado.</li>
 *     <li>{@link #delete(String)} -> apaga o arquivo de uma mensagem excluída.</li>
 * </ul>
 */
@Component
public class GcsMediaStore implements MediaStore {

    private static final String KEY_PREFIX = "media/";
    private static final String CONVERSATION_HEADER = "x-goog-meta-conversation-id";
    private static final String UPLOADER_HEADER = "x-goog-meta-uploader";
    private static final String LENGTH_RANGE_HEADER = "x-goog-content-length-range";
    private static final String CONVERSATION_METADATA = "conversation-id";
    private static final String UPLOADER_METADATA = "uploader";

    private final MediaProperties properties;
    private final Supplier<Storage> storage;

    @Autowired
    public GcsMediaStore(MediaProperties properties) {
        this(properties, lazyDefaultStorage());
    }

    GcsMediaStore(MediaProperties properties, Supplier<Storage> storage) {
        this.properties = properties;
        this.storage = storage;
    }

    @Override
    public MediaTicket createUpload(String mediaId, String conversationId, UUID uploader, String mimeType, long sizeBytes) {
        String bucket = requireBucket();
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Content-Type", mimeType);
        headers.put(LENGTH_RANGE_HEADER, sizeBytes + "," + sizeBytes);
        headers.put(CONVERSATION_HEADER, conversationId);
        headers.put(UPLOADER_HEADER, uploader.toString());
        // na assinatura V4 o tipo só entra como cabeçalho assinado explícito; withContentType() sozinho não o inclui
        URL url = storage.get().signUrl(BlobInfo.newBuilder(BlobId.of(bucket, KEY_PREFIX + mediaId)).setContentType(mimeType).build(),
                properties.uploadValidity().toSeconds(), TimeUnit.SECONDS,
                Storage.SignUrlOption.httpMethod(HttpMethod.PUT), Storage.SignUrlOption.withV4Signature(),
                Storage.SignUrlOption.withExtHeaders(headers));
        return new MediaTicket(url.toString(), "PUT", headers, Instant.now().plus(properties.uploadValidity()));
    }

    @Override
    public MediaTicket createDownload(String mediaId) {
        String bucket = requireBucket();
        URL url = storage.get().signUrl(BlobInfo.newBuilder(BlobId.of(bucket, KEY_PREFIX + mediaId)).build(),
                properties.downloadValidity().toSeconds(), TimeUnit.SECONDS,
                Storage.SignUrlOption.httpMethod(HttpMethod.GET), Storage.SignUrlOption.withV4Signature(),
                Storage.SignUrlOption.withQueryParams(Map.of("response-content-disposition", "attachment")));
        return new MediaTicket(url.toString(), "GET", Map.of(), Instant.now().plus(properties.downloadValidity()));
    }

    @Override
    public Optional<StoredMedia> stat(String mediaId) {
        String bucket = requireBucket();
        Blob blob = storage.get().get(BlobId.of(bucket, KEY_PREFIX + mediaId));
        if (blob == null || blob.getMetadata() == null) return Optional.empty();
        String conversationId = blob.getMetadata().get(CONVERSATION_METADATA);
        String uploader = blob.getMetadata().get(UPLOADER_METADATA);
        // sem dono ou conversa não dá para autorizar: um objeto que não veio deste fluxo é tratado como ausente
        if (conversationId == null || uploader == null) return Optional.empty();
        try {
            return Optional.of(new StoredMedia(conversationId, UUID.fromString(uploader), blob.getContentType(),
                    blob.getSize() == null ? 0 : blob.getSize(), blob.getCreateTimeOffsetDateTime().toInstant()));
        } catch (IllegalArgumentException invalidOwner) {
            return Optional.empty();
        }
    }

    @Override
    public void delete(String mediaId) {
        storage.get().delete(BlobId.of(requireBucket(), KEY_PREFIX + mediaId));
    }

    private String requireBucket() {
        String bucket = properties.bucket();
        if (bucket == null || bucket.isBlank()) {
            throw new DependencyUnavailableException("O armazenamento de mídia não está configurado.");
        }
        return bucket;
    }

    /** Cria o cliente no primeiro uso, para o serviço subir sem credencial do Google quando a mídia está desligada. */
    private static Supplier<Storage> lazyDefaultStorage() {
        return new Supplier<>() {
            private volatile Storage instance;

            @Override
            public Storage get() {
                Storage current = instance;
                if (current == null) {
                    synchronized (this) {
                        if (instance == null) instance = StorageOptions.getDefaultInstance().getService();
                        current = instance;
                    }
                }
                return current;
            }
        };
    }
}
