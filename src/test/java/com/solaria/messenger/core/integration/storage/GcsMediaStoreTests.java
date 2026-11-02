package com.solaria.messenger.core.integration.storage;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import java.net.URI;
import java.security.KeyPairGenerator;
import java.security.PrivateKey;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.util.unit.DataSize;

import com.google.auth.oauth2.ServiceAccountCredentials;
import com.google.cloud.storage.Blob;
import com.google.cloud.storage.BlobId;
import com.google.cloud.storage.Storage;
import com.google.cloud.storage.StorageOptions;
import com.solaria.messenger.core.exception.DependencyUnavailableException;
import com.solaria.messenger.core.model.MediaTicket;
import com.solaria.messenger.core.model.StoredMedia;

/**
 * Cobre o armazenamento no Google Cloud Storage. A assinatura é real e local (chave gerada na hora, sem rede): confere o
 * que entra nela. A consulta ao objeto usa um {@link Storage} simulado.
 */
class GcsMediaStoreTests {

    private static final UUID UPLOADER = UUID.fromString("11111111-1111-1111-1111-111111111111");

    private Storage signing;
    private MediaProperties properties;

    @BeforeEach
    void setUp() throws Exception {
        KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
        generator.initialize(2048);
        PrivateKey key = generator.generateKeyPair().getPrivate();
        ServiceAccountCredentials credentials = ServiceAccountCredentials.newBuilder()
                .setClientEmail("messenger@projeto.iam.gserviceaccount.com").setPrivateKey(key).setPrivateKeyId("k1").build();
        signing = StorageOptions.newBuilder().setProjectId("projeto").setCredentials(credentials).build().getService();
        properties = properties("bucket-de-midia");
    }

    @Test
    void uploadTicketIsASignedPutWhoseSignatureCoversTypeSizeAndOwner() {
        MediaTicket ticket = new GcsMediaStore(properties, () -> signing)
                .createUpload("m1", "conv-1", UPLOADER, "image/jpeg", 2048);

        URI url = URI.create(ticket.url());
        assertThat(ticket.method()).isEqualTo("PUT");
        assertThat(url.getHost()).contains("storage.googleapis.com");
        assertThat(url.getPath()).endsWith("/media/m1");
        assertThat(ticket.url()).contains("bucket-de-midia");
        assertThat(url.getQuery()).contains("X-Goog-Algorithm=GOOG4-RSA-SHA256").contains("X-Goog-Expires=900");
        // o que o cliente precisa mandar igual, porque entra na assinatura
        assertThat(ticket.headers()).containsEntry("Content-Type", "image/jpeg")
                .containsEntry("x-goog-content-length-range", "2048,2048")
                .containsEntry("x-goog-meta-conversation-id", "conv-1")
                .containsEntry("x-goog-meta-uploader", UPLOADER.toString());
        String signedHeaders = queryValue(url, "X-Goog-SignedHeaders");
        assertThat(signedHeaders).contains("content-type", "x-goog-content-length-range", "x-goog-meta-conversation-id", "x-goog-meta-uploader");
        assertThat(ticket.expiresAt()).isAfter(Instant.now()).isBefore(Instant.now().plus(Duration.ofMinutes(16)));
    }

    @Test
    void downloadTicketIsASignedGetThatForcesDownloadAndNeedsNoHeaders() {
        MediaTicket ticket = new GcsMediaStore(properties, () -> signing).createDownload("m1");

        URI url = URI.create(ticket.url());
        assertThat(ticket.method()).isEqualTo("GET");
        assertThat(ticket.headers()).isEmpty();
        assertThat(url.getPath()).endsWith("/media/m1");
        assertThat(url.getQuery()).contains("X-Goog-Expires=600").contains("response-content-disposition=attachment");
    }

    @Test
    void statReadsTheOwnerConversationTypeSizeAndCreationTime() {
        Storage storage = mock(Storage.class);
        Blob blob = mock(Blob.class);
        given(storage.get(BlobId.of("bucket-de-midia", "media/m1"))).willReturn(blob);
        given(blob.getMetadata()).willReturn(Map.of("conversation-id", "conv-1", "uploader", UPLOADER.toString()));
        given(blob.getContentType()).willReturn("image/jpeg");
        given(blob.getSize()).willReturn(2048L);
        given(blob.getCreateTimeOffsetDateTime()).willReturn(OffsetDateTime.of(2026, 11, 2, 10, 0, 0, 0, ZoneOffset.UTC));

        Optional<StoredMedia> stored = new GcsMediaStore(properties, () -> storage).stat("m1");

        assertThat(stored).contains(new StoredMedia("conv-1", UPLOADER, "image/jpeg", 2048, Instant.parse("2026-11-02T10:00:00Z")));
    }

    @Test
    void aMissingObjectOrOneNotDepositedByThisFlowIsAbsent() {
        Storage storage = mock(Storage.class);
        Blob foreign = mock(Blob.class);
        given(storage.get(BlobId.of("bucket-de-midia", "media/foreign"))).willReturn(foreign);
        given(foreign.getMetadata()).willReturn(null);

        GcsMediaStore store = new GcsMediaStore(properties, () -> storage);

        assertThat(store.stat("gone")).isEmpty();
        assertThat(store.stat("foreign")).isEmpty();
    }

    @Test
    void deleteRemovesTheObjectAndAMissingOneIsNotAnError() {
        Storage storage = mock(Storage.class);
        given(storage.delete(BlobId.of("bucket-de-midia", "media/m1"))).willReturn(false);

        new GcsMediaStore(properties, () -> storage).delete("m1");

        org.mockito.Mockito.verify(storage).delete(BlobId.of("bucket-de-midia", "media/m1"));
    }

    @Test
    void withoutABucketEveryOperationIsUnavailable() {
        GcsMediaStore store = new GcsMediaStore(properties(""), () -> signing);

        assertThatThrownBy(() -> store.createUpload("m1", "conv-1", UPLOADER, "image/jpeg", 1))
                .isInstanceOf(DependencyUnavailableException.class);
        assertThatThrownBy(() -> store.createDownload("m1")).isInstanceOf(DependencyUnavailableException.class);
        assertThatThrownBy(() -> store.stat("m1")).isInstanceOf(DependencyUnavailableException.class);
        assertThatThrownBy(() -> store.delete("m1")).isInstanceOf(DependencyUnavailableException.class);
    }

    private static MediaProperties properties(String bucket) {
        return new MediaProperties(bucket, Duration.ofDays(7), Duration.ofMinutes(15), Duration.ofMinutes(10),
                DataSize.ofMegabytes(10), DataSize.ofMegabytes(16), DataSize.ofMegabytes(64), DataSize.ofMegabytes(25));
    }

    private static String queryValue(URI url, String name) {
        for (String pair : url.getQuery().split("&")) {
            if (pair.startsWith(name + "=")) return java.net.URLDecoder.decode(pair.substring(name.length() + 1), java.nio.charset.StandardCharsets.UTF_8);
        }
        return "";
    }
}
