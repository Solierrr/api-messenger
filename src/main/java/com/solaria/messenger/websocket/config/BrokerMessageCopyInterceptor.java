package com.solaria.messenger.websocket.config;

import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.SimpMessageHeaderAccessor;
import org.springframework.messaging.simp.SimpMessageType;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageBuilder;

/**
 * O user pode ter mais de uma sessão privada, para o envio em uma sessão não atrapalhar as demais, 
 * é gerado uma cópia da mensagem, para as outras sessões usarem.
 * apenas o cabeçalho é editado, o resto da mensagem são inalterados.
 */
class BrokerMessageCopyInterceptor implements ChannelInterceptor {

    /** Copia mensagens com cabeçalhos mutáveis, os demais tipos passam inalterados */
@Override
public Message<?> preSend(Message<?> message, MessageChannel channel) {

    // eventos diferentes de MESSAGE não precisam ter duplicação
    if (SimpMessageHeaderAccessor.getMessageType(message.getHeaders()) != SimpMessageType.MESSAGE) {
        return message;
    }

    SimpMessageHeaderAccessor headers = SimpMessageHeaderAccessor.wrap(message);

    headers.setLeaveMutable(true);

    // monta uma nova mensagem com cabeçalhos editáveis
    // essa cópia segue para o broker e é travada para edições quando chega pelo próprio broker | a original não é enviada
    return MessageBuilder.createMessage(message.getPayload(), headers.getMessageHeaders());
}
}
