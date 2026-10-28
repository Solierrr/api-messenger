package com.solaria.messenger.websocket.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Propriedades do relay STOMP com o RabbitMQ (infra-broker)
 *
 * <p>Centraliza o endereço e as credenciais do broker</p>
 *
 * @param host host do RabbitMQ (Service {@code broker} no cluster)
 * @param stompPort porta do plugin STOMP do RabbitMQ (61613)
 * @param login usuário do RabbitMQ
 * @param passcode senha do RabbitMQ
 * @param autoStartup {@code false} só em teste
 */
@ConfigurationProperties(prefix = "app.broker")
public record BrokerProperties(String host, int stompPort, String login, String passcode, boolean autoStartup) {
}
