package com.solaria.messenger.websocket.config;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;

/** Cobre o default do relay: ligado só com host ou com a flag explícita, e a flag sempre prevalece. */
class BrokerPropertiesTests {

    @Configuration
    @EnableConfigurationProperties(BrokerProperties.class)
    static class Config { }

    private final ApplicationContextRunner runner = new ApplicationContextRunner().withUserConfiguration(Config.class);

    @Test
    void relayStaysOffWithoutHostOrFlag() {
        runner.withPropertyValues("app.broker.enabled=", "app.broker.host=", "app.broker.stomp-port=61613")
                .run(context -> assertFalse(context.getBean(BrokerProperties.class).enabled()));
    }

    @Test
    void relayTurnsOnWhenTheHostIsSet() {
        runner.withPropertyValues("app.broker.enabled=", "app.broker.host=broker", "app.broker.stomp-port=61613")
                .run(context -> assertTrue(context.getBean(BrokerProperties.class).enabled()));
    }

    @Test
    void explicitFlagOverridesTheHost() {
        runner.withPropertyValues("app.broker.enabled=false", "app.broker.host=broker", "app.broker.stomp-port=61613")
                .run(context -> assertFalse(context.getBean(BrokerProperties.class).enabled()));
        runner.withPropertyValues("app.broker.enabled=true", "app.broker.host=", "app.broker.stomp-port=61613")
                .run(context -> assertTrue(context.getBean(BrokerProperties.class).enabled()));
    }
}
