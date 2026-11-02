<p align="center">
  <img src="../assets/readme/banners/springboot.png" alt="Spring Boot" width="100%">
</p>

# Conversa direta

Acontece quando uma pessoa abre conversa com outra. O resultado é a conversa direta do par, nova ou a mesma de antes. Cada par tem uma conversa direta só, guardada pela chave `direct_key`, que é os dois UUIDs ordenados e unidos por `|`. O `CreateDirectConversationUseCase` faz o fluxo.

```mermaid
sequenceDiagram
    participant App as web-app ou mobile-app
    participant Msg as api-messenger
    participant Auth as api-auth
    participant Mongo as MongoDB

    App->>Msg: POST /messaging/conversations/direct (JWT)
    Msg->>Auth: atividade das duas contas
    Auth-->>Msg: ativas
    Msg->>Mongo: busca a direta pela chave do par
    alt já existe
        Msg-->>App: 201 com a mesma conversa
    else não existe
        Msg->>Mongo: grava a conversa e o estado das duas pessoas numa transação
        alt outra requisição criou o par antes
            Mongo-->>Msg: chave duplicada
            Msg->>Mongo: lê a conversa vencedora
        end
        Msg-->>App: 201 com a conversa
    end
    Msg-->>App: CONVERSATION_UPDATED às duas pessoas, se criou
```

## Erros

- Destinatário vazio ou igual a quem pede dá `400 INVALID_FIELD`.
- Conta inexistente dá `404 RESOURCE_NOT_FOUND`, e conta inativa dá `403 UNAUTHORIZED_ACCESS`.
- Conversa encontrada que não corresponde ao par dá `409 DIRECT_IDENTITY_INCONSISTENT`.
- Estado pessoal ausente ou inválido de uma das pessoas dá `422 BUSINESS_RULE_VIOLATION`.
- Falha técnica na criação dá `503 CONVERSATION_PERSISTENCE_UNCERTAIN`.
