package com.solaria.messenger.websocket.config;

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

import com.solaria.messenger.websocket.security.StompAuthChannelInterceptor;
import com.solaria.messenger.websocket.security.ResourceOutboundChannelInterceptor;
import com.solaria.messenger.websocket.session.LocalWebSocketSessionRegistry;
import com.solaria.messenger.core.service.ConversationAuthorizationService;

/**
 * Configuracao do WebSocket/STOMP
 *
 * - endpoint STOMP em /ws
 * - prefixo /app para destinos de comando
 * - heartbeat 10s/10s e autenticacao real do CONNECT via {@link StompAuthChannelInterceptor}
 * - endpoints websocket registrados em /messanging/ws por conta do Kong(gateway)
 * - Kong derruba conexoes ociosas apos ~60s,
 * heartbeat STOMP 10s/10s garante trafego continuo bem abaixo desse timeout
 */
@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private static final long[] HEARTBEAT_MS = {10_000, 10_000};

    private final StompAuthChannelInterceptor stompAuthChannelInterceptor;
    private final LocalWebSocketSessionRegistry webSocketSessionRegistry;
    private final ConversationAuthorizationService authorization;

    public WebSocketConfig(StompAuthChannelInterceptor stompAuthChannelInterceptor,
            LocalWebSocketSessionRegistry webSocketSessionRegistry, ConversationAuthorizationService authorization) {
        this.stompAuthChannelInterceptor = stompAuthChannelInterceptor;
        this.webSocketSessionRegistry = webSocketSessionRegistry;
        this.authorization = authorization;
    }

    /** registra rotas que o cliente se conecta */
    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        // processamento de frames do mesmo client são processados na ordem que chegam
        registry.setPreserveReceiveOrder(true);
        // /ws sem gwt dev(local)  /messaging/ws dev(local) com gwt, prod e QA
        registry.addEndpoint("/ws", "/messaging/ws")
                .setAllowedOriginPatterns("*"); // aceita qualquer origem
    }

    /** Broker simples em /topic e /queue com heartbeat, prefixo /app e ordem de publicação preservada. */
    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.configureBrokerChannel().interceptors(new BrokerMessageCopyInterceptor());
        registry.enableSimpleBroker("/topic", "/queue")
                .setHeartbeatValue(HEARTBEAT_MS)
                .setTaskScheduler(heartbeatTaskScheduler());
        registry.setApplicationDestinationPrefixes("/app");
        registry.setPreservePublishOrder(true);
    }

    /** Autentica e autoriza os frames de entrada */
    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(stompAuthChannelInterceptor);
    }

    /** Revalida sessão e participação em cada frame de saída */
    @Override
    public void configureClientOutboundChannel(ChannelRegistration registration) {
        registration.interceptors(new ResourceOutboundChannelInterceptor(webSocketSessionRegistry, authorization));
    }

    /** Exige o primeiro frame em até 10s e acopla o registro local de sessões ao transporte. */
    @Override
    public void configureWebSocketTransport(WebSocketTransportRegistration registration) {
        registration.setTimeToFirstMessage(10_000);
        registration.addDecoratorFactory(handler -> new SessionRegistryHandlerDecorator(handler, webSocketSessionRegistry));
    }

    /** Agendador dos heartbeats STOMP. */
    @Bean
    @Primary
    public TaskScheduler heartbeatTaskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(4);
        scheduler.setThreadNamePrefix("ws-heartbeat-");
        return scheduler;
    }
}
