package com.solaria.messenger.core.helper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.util.unit.DataSize;

import com.solaria.messenger.core.exception.InvalidFieldException;
import com.solaria.messenger.core.integration.storage.MediaProperties;
import com.solaria.messenger.core.model.enums.AttachmentType;

/** Cobre o que o servidor aceita receber: tipo do arquivo, formato do mime e tamanho máximo por categoria. */
class MediaRulesHelperTests {

    private static final long MB = 1024 * 1024;

    private final MediaRulesHelper rules = new MediaRulesHelper(new MediaProperties("bucket", Duration.ofDays(7),
            Duration.ofMinutes(15), Duration.ofMinutes(10), DataSize.ofMegabytes(10), DataSize.ofMegabytes(16),
            DataSize.ofMegabytes(64), DataSize.ofMegabytes(25)));

    @Test
    void imageAcceptsTheAllowedMimesIgnoringCaseAndParameters() {
        assertThatCode(() -> rules.requireAllowed(AttachmentType.IMAGE, "image/jpeg", 1 * MB)).doesNotThrowAnyException();
        assertThatCode(() -> rules.requireAllowed(AttachmentType.IMAGE, "IMAGE/PNG; q=1", 1 * MB)).doesNotThrowAnyException();
        assertThatCode(() -> rules.requireAllowed(AttachmentType.IMAGE, "image/webp", 1)).doesNotThrowAnyException();
    }

    @Test
    void imageWithAVideoMimeIsRefused() {
        assertThatThrownBy(() -> rules.requireAllowed(AttachmentType.IMAGE, "video/mp4", 1 * MB))
                .isInstanceOf(InvalidFieldException.class);
    }

    @Test
    void audioAcceptsTheCommonRecordingFormatsAndRefusesOthers() {
        for (String mime : new String[] { "audio/mp4", "audio/aac", "audio/mpeg", "audio/ogg", "audio/webm" }) {
            assertThatCode(() -> rules.requireAllowed(AttachmentType.AUDIO, mime, 1 * MB)).doesNotThrowAnyException();
        }
        assertThatThrownBy(() -> rules.requireAllowed(AttachmentType.AUDIO, "image/png", 1 * MB))
                .isInstanceOf(InvalidFieldException.class);
    }

    @Test
    void fileAcceptsAnyWellFormedMimeButNotAMalformedOne() {
        assertThatCode(() -> rules.requireAllowed(AttachmentType.FILE, "application/pdf", 1 * MB)).doesNotThrowAnyException();
        assertThatCode(() -> rules.requireAllowed(AttachmentType.FILE, "application/x-qualquer-coisa", 1 * MB)).doesNotThrowAnyException();
        assertThatThrownBy(() -> rules.requireAllowed(AttachmentType.FILE, "binario", 1 * MB))
                .isInstanceOf(InvalidFieldException.class);
    }

    @Test
    void eachCategoryHasItsOwnSizeCeiling() {
        assertThat(rules.maxBytes(AttachmentType.IMAGE)).isEqualTo(10 * MB);
        assertThat(rules.maxBytes(AttachmentType.AUDIO)).isEqualTo(16 * MB);
        assertThat(rules.maxBytes(AttachmentType.VIDEO)).isEqualTo(64 * MB);
        assertThat(rules.maxBytes(AttachmentType.FILE)).isEqualTo(25 * MB);
        assertThatCode(() -> rules.requireAllowed(AttachmentType.IMAGE, "image/jpeg", 10 * MB)).doesNotThrowAnyException();
        assertThatThrownBy(() -> rules.requireAllowed(AttachmentType.IMAGE, "image/jpeg", 10 * MB + 1))
                .isInstanceOf(InvalidFieldException.class);
        assertThatThrownBy(() -> rules.requireAllowed(AttachmentType.VIDEO, "video/mp4", 64 * MB + 1))
                .isInstanceOf(InvalidFieldException.class);
    }

    @Test
    void emptyOrNegativeSizesAreRefused() {
        assertThatThrownBy(() -> rules.requireAllowed(AttachmentType.FILE, "application/pdf", 0))
                .isInstanceOf(InvalidFieldException.class);
        assertThatThrownBy(() -> rules.requireAllowed(AttachmentType.FILE, "application/pdf", -5))
                .isInstanceOf(InvalidFieldException.class);
    }

    @Test
    void locationHasNoFileToStore() {
        assertThatThrownBy(() -> rules.requireAllowed(AttachmentType.LOCATION, "text/plain", 1))
                .isInstanceOf(InvalidFieldException.class);
    }
}
