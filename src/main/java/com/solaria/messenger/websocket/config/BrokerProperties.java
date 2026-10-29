package com.solaria.messenger.websocket.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Propriedades do relay STOMP com o RabbitMQ (infra-broker)
 *
 * <p>Centraliza o endereço e as credenciais do broker</p>
 *
 * @param enabled {@code true} usa o relay para o RabbitMQ (vários pods); {@code false} usa o broker em memória do Spring
 *        (um pod só). Sem valor, o relay é ligado quando {@code host} está preenchido
 * @param host host do RabbitMQ (Service {@code broker} no cluster)
 * @param stompPort porta do plugin STOMP do RabbitMQ (61613)
 * @param login usuário do RabbitMQ
 * @param passcode senha do RabbitMQ
 * @param autoStartup {@code false} só em teste
 */
@ConfigurationProperties(prefix = "app.broker")
public record BrokerProperties(Boolean enabled, String host, int stompPort, String login, String passcode,
        boolean autoStartup) {

    public BrokerProperties {
        if (enabled == null) enabled = host != null && !host.isBlank();
    }
}
