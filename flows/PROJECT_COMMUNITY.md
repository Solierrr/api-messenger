<p align="center">
  <img src="../assets/readme/banners/springboot.png" alt="Spring Boot" width="100%">
</p>

# Comunidade de projeto

Acontece quando o demandante de um projeto cria a comunidade e quando os membros entram, mudam de papel ou saem. O resultado é a comunidade com os membros e os subgrupos coerentes entre si. O fluxo central de [comunidade de projeto](https://github.com/Solierrr/docs-warehouse/blob/main/docs/flows/PROJECT_COMMUNITY.md) descreve a comunidade sincronizada com a equipe do projeto no `api-core`. Este serviço faz hoje só a parte abaixo.

## Criar

```mermaid
sequenceDiagram
    participant App as web-app ou mobile-app
    participant Msg as api-messenger
    participant Core as api-core
    participant Auth as api-auth
    participant Mongo as MongoDB

    App->>Msg: POST /messaging/communities (JWT)
    Msg->>Core: GET /internal/technical-projects/{projectId}/requester-access/{authId} (X-Service-Key)
    alt não é o demandante
        Core-->>Msg: 403
        Msg-->>App: 403 UNAUTHORIZED_ACCESS
    else é o demandante
        Core-->>Msg: 200
        Msg->>Auth: atividade dos membros iniciais
        Msg->>Mongo: grava a comunidade, com o criador como OWNER e os aceitos como MEMBER
        Msg-->>App: 201 com a comunidade e os recusados
    end
```

## Remover um membro ou sair

A remoção passa pelos subgrupos antes de terminar, para que nenhum grupo fique sem gestor. O `RemoveCommunityMemberUseCase` valida o plano inteiro antes de escrever.

```mermaid
flowchart TD
    A[DELETE /messaging/communities/id/members/userId] --> B{alvo é o OWNER?}
    B -- sim --> E1[422]
    B -- não --> C{sair sozinho, OWNER removendo, ou ADMIN removendo um MEMBER?}
    C -- não --> E2[403]
    C -- sim --> D[valida os sucessores de cada subgrupo que o alvo gere]
    D --> F[passa a gestão dos subgrupos aos sucessores]
    F --> G[tira o alvo da comunidade]
    G --> H[CONVERSATION_REMOVED de cada subgrupo ao alvo]
    H --> I[tira o alvo dos subgrupos e grava a versão da saída]
    I --> I2[apaga o subgrupo que ficou vazio]
    I2 --> J[revoga as sessões do alvo e avisa os subgrupos]
    J --> K[204]
```

O acesso aos subgrupos acaba no passo em que o alvo sai da comunidade, porque a autorização exige ser membro atual. Se uma falha técnica interromper a cascata, o mesmo pedido continua de onde parou.

## Papéis e status

- Adicionar membros e conceder `ADMIN` exigem `OWNER` ou `ADMIN`. Rebaixar ou remover um `ADMIN` exige `OWNER`. `OWNER` nunca é atribuído por pedido.
- Convidados inexistentes ou inativos ficam de fora e voltam em `rejectedParticipants`. Se ninguém puder entrar, a resposta é `422`.
- Arquivar e reativar exigem `OWNER` ou `ADMIN`. Mudar o status avisa todos os subgrupos.
- A comunidade nunca é apagada.
- Quem ainda está em algum subgrupo não volta à comunidade até sair deles.

## Erros

- Projeto ou pessoa inexistente no `api-core` dá `404 RESOURCE_NOT_FOUND`, e o `api-core` fora do ar dá `503 DEPENDENCY_UNAVAILABLE`.
- Projeto que já tem comunidade dá `409 DUPLICATE_RESOURCE`.
- Atribuir `OWNER` dá `400 INVALID_FIELD`.
- Comunidade arquivada em operação de administração, ou nenhum convidado aceito, dá `422 BUSINESS_RULE_VIOLATION`.
- Readmissão com resto em subgrupo dá `409 MEMBERSHIP_CLEANUP_REQUIRED`.
- Sucessor ausente ou inválido dá `409 SUCCESSOR_REQUIRED` ou `409 SUCCESSOR_INELIGIBLE`.
- Falha no meio da cascata dá `503 REMOVAL_INCOMPLETE`.
