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
 * Configuração do WebSocket/STOMP.
 *
 * <p>
 * Pontos da configuração:
 * </p>
 * <ul>
 *     <li>endpoint STOMP em {@code /ws}, também registrado em {@code /messaging/ws} por conta do Kong (gateway);</li>
 *     <li>prefixo {@code /app} para destinos de comando;</li>
 *     <li>autenticação real do CONNECT via {@link StompAuthChannelInterceptor};</li>
 *     <li>broker externo: relay STOMP para o RabbitMQ, que entrega os eventos a todos os pods;</li>
 *     <li>sem relay habilitado ({@code app.broker.enabled}), broker em memória do Spring, válido para um pod só (QA);</li>
 *     <li>Kong derruba conexões ociosas após ~60s, e o heartbeat 10s/10s pedido pelo cliente é negociado com o
 *         RabbitMQ.</li>
 * </ul>
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
     * Com o relay desligado, o broker em memória atende os mesmos destinos e negocia o mesmo heartbeat de 10s.
     */
    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        if (broker.enabled()) {
            enableRelay(registry);
        } else {
            registry.enableSimpleBroker("/topic")
                    .setHeartbeatValue(new long[] { 10_000, 10_000 })
                    .setTaskScheduler(heartbeatTaskScheduler());
        }
        registry.setApplicationDestinationPrefixes("/app");
        registry.setPreservePublishOrder(true);
    }

    private void enableRelay(MessageBrokerRegistry registry) {
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
