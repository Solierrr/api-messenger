package com.solaria.messenger.core.helper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.util.unit.DataSize;

import com.solaria.messenger.core.contract.request.AttachmentRequestDTO;
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
 * Cobre o anexo aceito no envio: a localização, e o arquivo, que só vale se o que foi depositado confere com o que a
 * mensagem declara (conversa, remetente, tamanho e tipo). O prazo do anexo sai da criação do arquivo.
 */
class AttachmentHelperTests {

    private static final UUID ME = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final UUID OTHER = UUID.fromString("22222222-2222-2222-2222-222222222222");
    private static final MediaProperties PROPERTIES = new MediaProperties("bucket", Duration.ofDays(7),
            Duration.ofMinutes(15), Duration.ofMinutes(10), DataSize.ofMegabytes(10), DataSize.ofMegabytes(16),
            DataSize.ofMegabytes(64), DataSize.ofMegabytes(25));

    private MediaStore store;
    private AttachmentHelper helper;

    @BeforeEach
    void setUp() {
        store = mock(MediaStore.class);
        helper = new AttachmentHelper(store, new MediaRulesHelper(PROPERTIES), PROPERTIES);
    }

    @Test
    void locationWithBothCoordinatesIsBuiltWithoutAFile() {
        AttachmentRequestDTO request = location(-23.55, -46.63);
        request.setLabel("Obra do cliente");

        Attachment attachment = helper.build("conv-1", ME, request);

        assertThat(attachment.getType()).isEqualTo(AttachmentType.LOCATION);
        assertThat(attachment.getLatitude()).isEqualTo(-23.55);
        assertThat(attachment.getLongitude()).isEqualTo(-46.63);
        assertThat(attachment.getLabel()).isEqualTo("Obra do cliente");
        assertThat(attachment.getMediaId()).isNull();
        assertThat(attachment.getExpiresAt()).isNull();
    }

    @Test
    void locationWithoutACoordinateIsRefused() {
        assertThatThrownBy(() -> helper.build("conv-1", ME, location(null, -46.63))).isInstanceOf(InvalidFieldException.class);
        assertThatThrownBy(() -> helper.build("conv-1", ME, location(-23.55, null))).isInstanceOf(InvalidFieldException.class);
    }

    @Test
    void attachmentWithoutATypeIsRefused() {
        assertThatThrownBy(() -> helper.build("conv-1", ME, new AttachmentRequestDTO())).isInstanceOf(InvalidFieldException.class);
    }

    @Test
    void depositedFileThatMatchesIsAcceptedWithTheExpiryCountedFromItsCreation() {
        Instant created = Instant.now().minus(Duration.ofDays(2));
        given(store.stat("m1")).willReturn(Optional.of(new StoredMedia("conv-1", ME, "image/jpeg", 2048, created)));
        AttachmentRequestDTO request = image("m1", "Image/JPEG; q=1", 2048);
        request.setWidth(800);
        request.setHeight(600);
        request.setClientPreview("LEHV6nWB2yk8");
        request.setDurationMs(5L);
        request.setName("ignorado.jpg");

        Attachment attachment = helper.build("conv-1", ME, request);

        assertThat(attachment.getType()).isEqualTo(AttachmentType.IMAGE);
        assertThat(attachment.getMediaId()).isEqualTo("m1");
        assertThat(attachment.getMimeType()).isEqualTo("image/jpeg");
        assertThat(attachment.getSizeBytes()).isEqualTo(2048L);
        assertThat(attachment.getExpiresAt()).isEqualTo(created.plus(Duration.ofDays(7)));
        assertThat(attachment.getWidth()).isEqualTo(800);
        assertThat(attachment.getHeight()).isEqualTo(600);
        assertThat(attachment.getClientPreview()).isEqualTo("LEHV6nWB2yk8");
        // campos que não fazem sentido para o tipo não são gravados
        assertThat(attachment.getDurationMs()).isNull();
        assertThat(attachment.getName()).isNull();
    }

    @Test
    void audioKeepsItsDurationAndFileKeepsItsName() {
        given(store.stat("a1")).willReturn(Optional.of(new StoredMedia("conv-1", ME, "audio/mp4", 100, Instant.now())));
        given(store.stat("f1")).willReturn(Optional.of(new StoredMedia("conv-1", ME, "application/pdf", 100, Instant.now())));
        AttachmentRequestDTO audio = file(AttachmentType.AUDIO, "a1", "audio/mp4", 100);
        audio.setDurationMs(4500L);
        AttachmentRequestDTO document = file(AttachmentType.FILE, "f1", "application/pdf", 100);
        document.setName("proposta.pdf");

        assertThat(helper.build("conv-1", ME, audio).getDurationMs()).isEqualTo(4500L);
        assertThat(helper.build("conv-1", ME, document).getName()).isEqualTo("proposta.pdf");
    }

    @Test
    void aFileThatWasNeverDepositedIsRefused() {
        given(store.stat("m1")).willReturn(Optional.empty());

        assertThatThrownBy(() -> helper.build("conv-1", ME, image("m1", "image/jpeg", 10)))
                .isInstanceOf(MediaNotUploadedException.class);
    }

    @Test
    void aFileOlderThanTheRetentionIsTreatedAsNotUploaded() {
        given(store.stat("m1")).willReturn(Optional.of(new StoredMedia("conv-1", ME, "image/jpeg", 10,
                Instant.now().minus(Duration.ofDays(8)))));

        assertThatThrownBy(() -> helper.build("conv-1", ME, image("m1", "image/jpeg", 10)))
                .isInstanceOf(MediaNotUploadedException.class);
    }

    @Test
    void aFileFromAnotherConversationOrAnotherSenderIsRefused() {
        given(store.stat("other-conv")).willReturn(Optional.of(new StoredMedia("conv-2", ME, "image/jpeg", 10, Instant.now())));
        given(store.stat("other-sender")).willReturn(Optional.of(new StoredMedia("conv-1", OTHER, "image/jpeg", 10, Instant.now())));

        assertThatThrownBy(() -> helper.build("conv-1", ME, image("other-conv", "image/jpeg", 10)))
                .isInstanceOf(MediaMismatchException.class);
        assertThatThrownBy(() -> helper.build("conv-1", ME, image("other-sender", "image/jpeg", 10)))
                .isInstanceOf(MediaMismatchException.class);
    }

    @Test
    void aSizeOrTypeDifferentFromWhatWasDepositedIsRefused() {
        given(store.stat("m1")).willReturn(Optional.of(new StoredMedia("conv-1", ME, "image/jpeg", 2048, Instant.now())));

        assertThatThrownBy(() -> helper.build("conv-1", ME, image("m1", "image/jpeg", 4096)))
                .isInstanceOf(MediaMismatchException.class);
        assertThatThrownBy(() -> helper.build("conv-1", ME, image("m1", "image/png", 2048)))
                .isInstanceOf(MediaMismatchException.class);
    }

    @Test
    void aFileAttachmentMissingItsFieldsOrBreakingTheRulesIsRefusedWithoutAskingTheStore() {
        AttachmentRequestDTO noId = image(null, "image/jpeg", 10);
        AttachmentRequestDTO noMime = image("m1", " ", 10);
        AttachmentRequestDTO noSize = image("m1", "image/jpeg", 10);
        noSize.setSizeBytes(null);

        assertThatThrownBy(() -> helper.build("conv-1", ME, noId)).isInstanceOf(InvalidFieldException.class);
        assertThatThrownBy(() -> helper.build("conv-1", ME, noMime)).isInstanceOf(InvalidFieldException.class);
        assertThatThrownBy(() -> helper.build("conv-1", ME, noSize)).isInstanceOf(InvalidFieldException.class);
        assertThatThrownBy(() -> helper.build("conv-1", ME, image("m1", "video/mp4", 10))).isInstanceOf(InvalidFieldException.class);
        org.mockito.Mockito.verifyNoInteractions(store);
    }

    @Test
    void storageFailureBecomesDependencyUnavailable() {
        given(store.stat("m1")).willThrow(new IllegalStateException("gcs fora"));

        assertThatThrownBy(() -> helper.build("conv-1", ME, image("m1", "image/jpeg", 10)))
                .isInstanceOf(DependencyUnavailableException.class);
    }

    private static AttachmentRequestDTO location(Double latitude, Double longitude) {
        AttachmentRequestDTO request = new AttachmentRequestDTO();
        request.setType(AttachmentType.LOCATION);
        request.setLatitude(latitude);
        request.setLongitude(longitude);
        return request;
    }

    private static AttachmentRequestDTO image(String mediaId, String mime, long size) {
        return file(AttachmentType.IMAGE, mediaId, mime, size);
    }

    private static AttachmentRequestDTO file(AttachmentType type, String mediaId, String mime, long size) {
        AttachmentRequestDTO request = new AttachmentRequestDTO();
        request.setType(type);
        request.setMediaId(mediaId);
        request.setMimeType(mime);
        request.setSizeBytes(size);
        return request;
    }
}
