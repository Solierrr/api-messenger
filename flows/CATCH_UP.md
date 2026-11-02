<p align="center">
  <img src="../assets/readme/banners/springboot.png" alt="Spring Boot" width="100%">
</p>

# Catch-up depois de reconectar

Acontece quando o aplicativo volta a se conectar e precisa do que perdeu. O resultado é o estado atual de cada mensagem que mudou depois da última versão que o cliente viu, nas conversas que ele lê hoje e nas que perdeu nesse meio-tempo. O `ListMessageUpdatesUseCase` atende `GET /messaging/updates`.

Toda mensagem gravada, editada ou apagada recebe em `change_version` uma versão do relógio `sync_clock`. Quando a pessoa sai de uma conversa, o estado pessoal dela recebe a versão da saída em `left_at_version`. O catch-up compara essas versões com o `since` do cliente.

```mermaid
sequenceDiagram
    participant App as web-app ou mobile-app
    participant Msg as api-messenger
    participant Mongo as MongoDB

    App->>Msg: SUBSCRIBE /topic/users.{id}.events
    App->>Msg: GET /messaging/updates?since={cursor guardado} (JWT)
    alt sem since
        Msg->>Mongo: versão atual do sync_clock
        Msg-->>App: syncEpoch e cursor, sem mensagens
    else com since
        Msg->>Mongo: conversas que a pessoa lê hoje e as que ela perdeu depois do since
        Msg->>Mongo: mensagens com change_version maior que o since
        Note over Msg,Mongo: nas conversas perdidas, só até a versão da saída
        Msg-->>App: syncEpoch, cursor, hasMore e mensagens
        opt hasMore verdadeiro
            App->>Msg: GET /messaging/updates?since={cursor novo}
        end
    end
```

- O cliente assina o tópico de eventos antes da consulta, para que nada que mude entre as duas coisas se perca. Uma mensagem que chega pelos dois caminhos tem o mesmo `id`.
- A resposta traz o estado atual de cada mensagem, inclusive as apagadas, sem o texto.
- O `cursor` é o `changeVersion` da última mensagem da página. Sem mensagens, ele repete o `since`.
- Um `syncEpoch` diferente do guardado quer dizer que o relógio foi recriado, e o cliente recarrega as conversas do zero.
- A consulta não altera nada.

## Erros

- `since` negativo dá `400 INVALID_FIELD`.
- Sem JWT válido dá `401 UNAUTHENTICATED`.
