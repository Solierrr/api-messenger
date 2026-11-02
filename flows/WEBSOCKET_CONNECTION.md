<p align="center">
  <img src="../assets/readme/banners/rabbitmq.png" alt="RabbitMQ" width="100%">
</p>

# Conexão em tempo real

Acontece quando o aplicativo abre o WebSocket e assina os tópicos. O resultado é uma sessão admitida, a presença da pessoa marcada como conectada e os eventos chegando enquanto ela tiver acesso. O protocolo completo está em [REALTIME.md](../docs/REALTIME.md).

```mermaid
sequenceDiagram
    participant App as web-app ou mobile-app
    participant Msg as api-messenger
    participant Auth as api-auth
    participant Redis as Redis
    participant Mongo as MongoDB

    App->>Msg: CONNECT em /messaging/ws (WebSocket + JWT)
    Msg->>Msg: valida o JWT e o sub
    Msg->>Auth: atividade da conta
    Msg->>Redis: conta as sessões da pessoa em todas as instâncias
    alt já tem 4 sessões
        Msg-->>App: ERROR SESSION_LIMIT_EXCEEDED e fecha
    else
        Msg->>Redis: grava a sessão
        Msg-->>App: CONNECTED
        Msg-->>App: USER_ONLINE nas conversas dela, se é a primeira sessão na instância
    end
    App->>Msg: SUBSCRIBE /topic/users.{id}.events, .acks e .errors
    App->>Msg: SUBSCRIBE /topic/conversations.{conversationId}
    Msg->>Mongo: confere a participação atual
    alt não participa
        Msg-->>App: erro SUBSCRIBE_NOT_ALLOWED ou UNAUTHORIZED_ACCESS em .errors
    else participa
        Msg-->>App: PRESENCE_SNAPSHOT só para esta sessão
    end
    opt a pessoa sai de um grupo ou da comunidade
        Msg->>Msg: fecha as sessões dela nesta instância (close 1008)
        Msg->>Redis: publica messenger:revoke
        Note over Msg,Redis: as outras instâncias fecham as sessões locais dela
    end
```

- O primeiro frame precisa chegar em até 10 s.
- A expiração do token é conferida em todo frame, e o token vencido fecha a conexão.
- Uma recusa de `SEND` ou de `SUBSCRIBE` descarta só o frame e não fecha a conexão.
- Cada evento que sai para o cliente passa de novo pela participação atual no Mongo.
- Depois de reconectar, o aplicativo busca o que perdeu pelo [catch-up](./CATCH_UP.md).

## Erros

- Sem `Authorization`, token inválido, `sub` fora do formato UUID, segundo CONNECT na mesma sessão ou conta inativa geram `ERROR` e fecham a sessão.
- A quinta sessão gera `SESSION_LIMIT_EXCEEDED`.
- Destino fora da lista gera `SEND_NOT_ALLOWED` ou `SUBSCRIBE_NOT_ALLOWED`, e comando STOMP fora da lista gera `COMMAND_NOT_ALLOWED`.
- Sem Redis, a presença e o limite de sessões usam só a visão da instância, sem erro para o cliente.
