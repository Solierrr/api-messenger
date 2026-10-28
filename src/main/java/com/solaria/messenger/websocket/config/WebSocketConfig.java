package com.solaria.messenger.websocket.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
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
import com.solaria.messenger.core.helper.ConversationAccessHelper;

/**
 * Configuracao do WebSocket/STOMP
 *
 * - endpoint STOMP em /ws
 * - prefixo /app para destinos de comando
 * - autenticacao real do CONNECT via {@link StompAuthChannelInterceptor}
 * - endpoints websocket registrados em /messanging/ws por conta do Kong(gateway)
 * - broker externo: relay STOMP para o RabbitMQ, que entrega os eventos a todos os pods
 * - Kong derruba conexoes ociosas apos ~60s; o heartbeat 10s/10s pedido pelo cliente é negociado com o RabbitMQ
 */
@Configuration
@EnableWebSocketMessageBroker
@EnableConfigurationProperties(BrokerProperties.class)
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final StompAuthChannelInterceptor stompAuthChannelInterceptor;
    private final LocalWebSocketSessionRegistry webSocketSessionRegistry;
    private final ConversationAccessHelper authorization;
    private final BrokerProperties broker;

    public WebSocketConfig(StompAuthChannelInterceptor stompAuthChannelInterceptor,
            LocalWebSocketSessionRegistry webSocketSessionRegistry, ConversationAccessHelper authorization,
            BrokerProperties broker) {
        this.stompAuthChannelInterceptor = stompAuthChannelInterceptor;
        this.webSocketSessionRegistry = webSocketSessionRegistry;
        this.authorization = authorization;
        this.broker = broker;
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

    /**
     * Relay de /topic para o RabbitMQ, prefixo /app e ordem de publicação preservada.
     * O RabbitMQ recusa '/' depois de /topic, por isso os destinos usam '.' (ver {@code StompDestinations}).
     */
    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableStompBrokerRelay("/topic")
                .setRelayHost(broker.host())
                .setRelayPort(broker.stompPort())
                // sem isto o relay manda o host do broker como vhost e o RabbitMQ recusa o CONNECT
                .setVirtualHost("/")
                .setClientLogin(broker.login())
                .setClientPasscode(broker.passcode())
                .setSystemLogin(broker.login())
                .setSystemPasscode(broker.passcode())
                .setAutoStartup(broker.autoStartup());
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

    /** Agendador dos jobs {@code @Scheduled}; {@code @Primary} desempata com o agendador interno do STOMP. */
    @Bean
    @Primary
    public TaskScheduler heartbeatTaskScheduler() {
        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
        scheduler.setPoolSize(4);
        scheduler.setThreadNamePrefix("ws-heartbeat-");
        return scheduler;
    }
}
