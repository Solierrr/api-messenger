<p align="center">
  <img src="../assets/readme/banners/rabbitmq.png" alt="RabbitMQ" width="100%">
</p>

<p align="center">
  <img src="../assets/readme/stacks/redis.png" width="100" hspace="8" alt="Redis" title="Redis">
</p>

# Tempo real

O cliente conecta por WebSocket e fala STOMP. Os comandos vão para destinos `/app/*`, e os eventos chegam por tópicos `/topic/*`. Com mais de uma instância, o Spring repassa os tópicos ao RabbitMQ por um relay STOMP, e o RabbitMQ entrega cada evento às sessões de qualquer instância. Com uma instância só, como no QA, o broker em memória do Spring atende os mesmos destinos. O Redis guarda a presença global e distribui a revogação de sessões.

## Conexão

| Item | Valor |
|---|---|
| Endpoints | `/ws` (local, sem gateway) e `/messaging/ws` (pelo Kong, com `strip-path: false`) |
| Origens | qualquer uma (`setAllowedOriginPatterns("*")`) |
| Primeiro frame | em até 10 s |
| Heartbeat | 10s/10s. O Kong fecha conexões ociosas depois de cerca de 60 s |
| Ordem | os frames de cada cliente são tratados e publicados na ordem em que chegam |
| Sessões por usuário | até 4, somadas em todas as instâncias. A quinta recebe `SESSION_LIMIT_EXCEEDED`, e as outras continuam abertas |

O CONNECT leva o header `Authorization`, com ou sem `Bearer`. O `StompAuthChannelInterceptor` valida o JWT com o mesmo decoder do HTTP, exige um `sub` UUID, recusa um segundo CONNECT na mesma sessão e consulta o `api-auth` para exigir conta ativa. Qualquer falha devolve um frame `ERROR` e fecha a sessão.

Depois do CONNECT:

- A expiração do JWT é conferida em todo frame. Token vencido devolve `ERROR` e fecha a conexão.
- Heartbeat, `UNSUBSCRIBE` e `DISCONNECT` passam direto.
- `SEND` aceita só os destinos da tabela de comandos. `SUBSCRIBE` aceita só os tópicos da tabela de destinos. Uma recusa descarta só aquele frame e manda o erro para `/topic/users.{userId}.errors`, sem fechar a conexão.
- Outros comandos STOMP, como `ACK`, `NACK` e `BEGIN`, geram `COMMAND_NOT_ALLOWED`.

## Comandos

| Destino | Payload | Efeito |
|---|---|---|
| `/app/messages.send` | o mesmo corpo de `POST /messaging/messages` | grava a mensagem e manda `MESSAGE_ACK` ao remetente |
| `/app/typing.started` | `{"conversationId"}` | publica `TYPING_STARTED` |
| `/app/typing.stopped` | `{"conversationId"}` | publica `TYPING_STOPPED` |
| `/app/presence.ping` | sem corpo | mantém a conexão ativa e não altera a presença |

## Destinos

O RabbitMQ recusa `/` depois de `/topic`, por isso o separador é `.`.

| Tópico | Quem assina | Conteúdo |
|---|---|---|
| `/topic/users.{userId}.events` | só o próprio usuário | `MESSAGE_CREATED`, `MESSAGE_EDITED`, `MESSAGE_DELETED`, `CONVERSATION_UPDATED` e `CONVERSATION_REMOVED` de todas as conversas dele |
| `/topic/users.{userId}.acks` | só o próprio usuário | `MESSAGE_ACK` |
| `/topic/users.{userId}.errors` | só o próprio usuário | `WsErrorPayload` |
| `/topic/conversations.{conversationId}` | participantes com acesso atual | presença, digitando e `PRESENCE_SNAPSHOT` |

- O id de conversa aceita só `[A-Za-z0-9_-]`, e o `userId` precisa ser um UUID em minúsculas. Isso impede assinar os curingas `*` e `#` do RabbitMQ.
- O `SUBSCRIBE` perde os headers `durable`, `persistent`, `auto-delete`, `exclusive` e `x-*`. A fila fica exclusiva e é apagada com a sessão, então o cliente não cria uma fila que acumule eventos depois de desconectar.
- O cliente assina `.events` antes de chamar `GET /messaging/updates`, para não perder o que mudar entre as duas coisas. O fluxo está em [CATCH_UP.md](../flows/CATCH_UP.md).

## Eventos

Todo evento usa `WsEventEnvelope`, com `type`, `conversationId`, `eventId`, `serverTimestamp` e `payload`.

| `type` | Tópico | Quando | `payload` |
|---|---|---|---|
| `MESSAGE_CREATED` | `users.{id}.events` | mensagem nova do usuário ou do assistente, para cada participante, inclusive o autor | `MessageResponseDTO`, com `changeVersion`. O `eventId` é o `id` da mensagem |
| `MESSAGE_EDITED` | `users.{id}.events` | edição efetiva | `MessageResponseDTO`, `eventId` novo |
| `MESSAGE_DELETED` | `users.{id}.events` | exclusão efetiva | a mensagem apagada, sem o texto, `eventId` novo |
| `MESSAGE_ACK` | `users.{id}.acks` | envio pelo STOMP | `MessageResponseDTO`. O `eventId` é o `id` da mensagem |
| `CONVERSATION_UPDATED` | `users.{id}.events` | mudança de conversa, mensagem, participantes ou status da comunidade. Depois de alterar o estado pessoal, só para o próprio usuário | o item da lista de conversas daquele destinatário |
| `CONVERSATION_REMOVED` | `users.{id}.events` | perda de participação | `{id, syncEpoch, syncVersion}`, sem participantes, conteúdo ou preferências |
| `PRESENCE_SNAPSHOT` | só a sessão que assinou a conversa | logo depois que o broker registra o `SUBSCRIBE` | `{subscriptionId, presenceEpoch, presenceVersion, users}` |
| `USER_ONLINE`, `USER_OFFLINE` | `conversations.{id}`, em cada conversa do usuário | a primeira ou a última sessão do usuário numa instância muda | `{userId, presenceEpoch, presenceVersion}` |
| `TYPING_STARTED`, `TYPING_STOPPED` | `conversations.{id}` | início e fim de digitando | `{userId}`, sem `eventId` |

O `WsErrorPayload` traz `code`, `message`, `clientMessageId` (quando o erro veio de um envio com correlação) e `traceId` (sempre nulo hoje). Os códigos são os mesmos do REST, descritos em [API.md](./API.md#erros).

Uma falha de entrega só gera log. Ela nunca desfaz a escrita nem vira erro do comando. O cliente recupera o que perdeu pelo catch-up e reconcilia a lista com `syncEpoch` e `syncVersion`.

## Filtro de saída

O `ResourceOutboundChannelInterceptor` confere cada frame antes de mandá-lo ao cliente. A sessão precisa estar admitida, sem revogação, sem `DISCONNECT` e com o socket aberto. Se o frame é de uma conversa, o usuário precisa participar dela agora, segundo o Mongo. Senão o frame é descartado.

O relay manda `ERROR "Connection to broker closed."` quando o RabbitMQ fecha a conexão depois de um `DISCONNECT` do cliente. O filtro descarta esse frame para sessões que já não operam, para o cliente não tratar o fim normal como queda.

## Presença

- O usuário está online quando tem ao menos uma sessão admitida em qualquer instância. Cada sessão fica em `messenger:presence:{userId}` no Redis.
- `USER_ONLINE` e `USER_OFFLINE` saem quando a primeira ou a última sessão numa instância muda, e só se o estado global ainda for o do evento.
- `presenceEpoch` e `presenceVersion` vêm do Redis. A época só muda se o Redis perder os dados.
- A cada 30 s, cada instância renova o próprio registro e regrava as sessões locais. Uma instância sem heartbeat por 90 s é considerada morta, e uma só instância limpa as sessões dela e publica `USER_OFFLINE`.
- A mesma presença decide quem recebe o aviso no celular. Uma falha ao consultar trata o usuário como desconectado.

Sem Redis, a presença, o snapshot e o limite usam só a visão da instância, com época `local-{uuid}`, e a instância continua recebendo tráfego.

## Digitando

- `typing.started` repetido em menos de 2 s é ignorado.
- `typing.started` exige participação e comunidade ativa. `typing.stopped` exige só participação.
- O estado fica em memória na instância da sessão. Uma varredura a cada 5 s remove entradas antigas sem publicar `TYPING_STOPPED`, então o cliente expira o indicador sozinho.
- Quando a última sessão do usuário numa instância cai, ela publica `TYPING_STOPPED` nas conversas em que ele digitava.

## Revogação de sessões

Quando alguém perde participação num grupo ou numa comunidade, o `WebSocketSessionRevoker` fecha todas as sessões dessa pessoa na instância atual, com close `1008`, e publica `origin:userId` no canal Redis `messenger:revoke`. As outras instâncias fecham as sessões locais. A pessoa pode reconectar em seguida, e cada assinatura passa de novo pela autorização.

A assinatura do canal tenta de novo a cada 30 s e não roda na partida, para que um Redis fora do ar não impeça a instância de subir. Sem Redis, as outras instâncias não recebem a revogação, mas o filtro de saída descarta os eventos das conversas perdidas.
