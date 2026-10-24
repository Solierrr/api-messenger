package com.solaria.messenger.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Primary;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.scheduling.TaskScheduler;
import org.springframework.scheduling.concurrent.ThreadPoolTaskScheduler;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;
import org.springframework.web.socket.config.annotation.WebSocketTransportRegistration;

import com.solaria.messenger.security.StompAuthChannelInterceptor;

/**
 * Configuracao do WebSocket/STOMP
 *
 * - endpoint STOMP em /ws
 * - prefixo /app para destinos de comando
 * - heartbeat 10s/10s e autenticacao real do CONNECT via {@link StompAuthChannelInterceptor}
 * - endpoints websocket registrados em /messenging/ws por conta do Kong(gateway)
 * - Kong derruba conexoes ociosas apos ~60s,
 * heartbeat STOMP 10s/10s garante trafego continuo bem abaixo desse timeout
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private static final long[] HEARTBEAT_MS = {10_000, 10_000};

    private final StompAuthChannelInterceptor stompAuthChannelInterceptor;

    public WebSocketConfig(StompAuthChannelInterceptor stompAuthChannelInterceptor) {
        this.stompAuthChannelInterceptor = stompAuthChannelInterceptor;
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // /ws sem gwt dev(local)  /messaging/ws dev(local) com gwt, prod e QA
        registry.addEndpoint("/ws", "/messaging/ws")
                .setAllowedOriginPatterns("*");
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic", "/queue")
                .setHeartbeatValue(HEARTBEAT_MS)
                .setTaskScheduler(heartbeatTaskScheduler());
        registry.setApplicationDestinationPrefixes("/app");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(stompAuthChannelInterceptor);
    }

    @Override
    public void configureWebSocketTransport(WebSocketTransportRegistration registration) {
        registration.setTimeToFirstMessage(10_000);
    }

    @Bean
    @Primary
    public TaskScheduler heartbeatTaskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(4);
        scheduler.setThreadNamePrefix("ws-heartbeat-");
        return scheduler;
    }
}
