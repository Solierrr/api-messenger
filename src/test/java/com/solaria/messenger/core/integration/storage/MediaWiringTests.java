package com.solaria.messenger.core.integration.storage;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.util.unit.DataSize;

import com.solaria.messenger.core.helper.MediaRulesHelper;
import com.solaria.messenger.core.model.enums.AttachmentType;
import com.solaria.messenger.core.port.MediaStore;

/**
 * Sobe só as peças da mídia, sem o resto da aplicação (o contexto completo não sobe nesta máquina): confere os padrões
 * das propriedades, a troca por configuração e a escolha do construtor do armazenamento pelo Spring.
 */
class MediaWiringTests {

    private final ApplicationContextRunner runner = new ApplicationContextRunner()
            .withUserConfiguration(MediaConfig.class, MediaRulesHelper.class, GcsMediaStore.class);

    @Test
    void defaultsMatchTheContract() {
        runner.run(context -> {
            MediaProperties properties = context.getBean(MediaProperties.class);
            assertThat(properties.ttl()).isEqualTo(Duration.ofDays(7));
            assertThat(properties.uploadValidity()).isEqualTo(Duration.ofMinutes(15));
            assertThat(properties.downloadValidity()).isEqualTo(Duration.ofMinutes(10));
            assertThat(properties.imageMax()).isEqualTo(DataSize.ofMegabytes(10));
            assertThat(properties.audioMax()).isEqualTo(DataSize.ofMegabytes(16));
            assertThat(properties.videoMax()).isEqualTo(DataSize.ofMegabytes(64));
            assertThat(properties.fileMax()).isEqualTo(DataSize.ofMegabytes(25));
            assertThat(properties.bucket()).isNull();
            assertThat(context.getBean(MediaRulesHelper.class).maxBytes(AttachmentType.VIDEO)).isEqualTo(64L * 1024 * 1024);
        });
    }

    @Test
    void configurationOverridesTheDefaults() {
        runner.withPropertyValues("app.media.bucket=meu-bucket", "app.media.ttl=P3D", "app.media.image-max=5MB").run(context -> {
            MediaProperties properties = context.getBean(MediaProperties.class);
            assertThat(properties.bucket()).isEqualTo("meu-bucket");
            assertThat(properties.ttl()).isEqualTo(Duration.ofDays(3));
            assertThat(properties.imageMax()).isEqualTo(DataSize.ofMegabytes(5));
        });
    }

    @Test
    void springBuildsTheStorageAdapterWithoutAGoogleCredential() {
        runner.run(context -> {
            assertThat(context).hasSingleBean(MediaStore.class);
            assertThat(context.getBean(MediaStore.class)).isInstanceOf(GcsMediaStore.class);
        });
    }
}
