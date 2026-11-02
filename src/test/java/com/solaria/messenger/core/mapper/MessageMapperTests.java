package com.solaria.messenger.core.mapper;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.api.Test;

import com.solaria.messenger.core.contract.response.AttachmentResponseDTO;
import com.solaria.messenger.core.contract.response.MessageResponseDTO;
import com.solaria.messenger.core.model.Attachment;
import com.solaria.messenger.core.model.Message;
import com.solaria.messenger.core.model.enums.AttachmentType;

/** Cobre o anexo no contrato público: arquivo leva o caminho da retirada, localização não, e sem anexo não há campo. */
class MessageMapperTests {

    @Test
    void fileAttachmentCarriesTheDescriptionAndTheDownloadPath() {
        Attachment attachment = new Attachment();
        attachment.setType(AttachmentType.IMAGE);
        attachment.setMediaId("m1");
        attachment.setMimeType("image/jpeg");
        attachment.setSizeBytes(2048L);
        attachment.setWidth(800);
        attachment.setHeight(600);
        attachment.setClientPreview("LEHV6nWB2yk8");
        attachment.setExpiresAt(Instant.parse("2026-11-09T10:00:00Z"));
        Message message = new Message();
        message.setAttachment(attachment);

        AttachmentResponseDTO response = MessageMapper.toResponse(message).getAttachment();

        assertThat(response.type()).isEqualTo(AttachmentType.IMAGE);
        assertThat(response.mediaId()).isEqualTo("m1");
        assertThat(response.mimeType()).isEqualTo("image/jpeg");
        assertThat(response.sizeBytes()).isEqualTo(2048L);
        assertThat(response.width()).isEqualTo(800);
        assertThat(response.clientPreview()).isEqualTo("LEHV6nWB2yk8");
        assertThat(response.expiresAt()).isEqualTo(Instant.parse("2026-11-09T10:00:00Z"));
        assertThat(response.downloadPath()).isEqualTo("/messaging/media/m1/content");
    }

    @Test
    void locationHasNoMediaIdExpiryOrDownloadPath() {
        Attachment attachment = new Attachment();
        attachment.setType(AttachmentType.LOCATION);
        attachment.setLatitude(-23.55);
        attachment.setLongitude(-46.63);
        attachment.setLabel("Obra");
        Message message = new Message();
        message.setAttachment(attachment);

        AttachmentResponseDTO response = MessageMapper.toResponse(message).getAttachment();

        assertThat(response.latitude()).isEqualTo(-23.55);
        assertThat(response.label()).isEqualTo("Obra");
        assertThat(response.mediaId()).isNull();
        assertThat(response.expiresAt()).isNull();
        assertThat(response.downloadPath()).isNull();
    }

    @Test
    void messageWithoutAttachmentHasNone() {
        MessageResponseDTO response = MessageMapper.toResponse(new Message());

        assertThat(response.getAttachment()).isNull();
    }
}
