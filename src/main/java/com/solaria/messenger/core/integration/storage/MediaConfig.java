package com.solaria.messenger.core.integration.storage;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Liga as propriedades de {@code app.media.*}. */
@Configuration
@EnableConfigurationProperties(MediaProperties.class)
public class MediaConfig {
}
