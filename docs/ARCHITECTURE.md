# Arquitetura do Repositório

O `api-messenger` segue uma arquitetura em camadas típica de um serviço Spring Boot (`controller` → `service` → `repository` → `model`), sem separação `service`/`service.impl` — as classes de serviço concentram a regra de negócio diretamente. Os `controller` implementam uma interface `openapi` dedicada por recurso (`ChatbotMessageOpenApi`, `ConversationOpenApi`, `MessageOpenApi`, `RatingOpenApi`, `LlmObservabilityOpenApi`), usada só para anotações do Springdoc e manter a classe do controller limpa. A persistência é 100% sobre MongoDB (`spring-boot-starter-data-mongodb`), sem banco relacional — cada agregado (`Conversation`, `Message`, `Rating`, `LlmObservability`) é um `@Document` próprio, sem transação distribuída entre eles.

<p>
  <a href="https://github.com/syvixor/skills-icons">
    <img src="https://skills.syvixor.com/api/icons?i=java,springboot,mongodb" height="48" alt="Arquitetura do api-messenger">
  </a>
</p>

- **Duas `SecurityFilterChain` segregadas por `@Order`** (`SecurityConfig`): a chain `@Order(1)` cobre só `/internal/**` com `permitAll`, protegida pela rede (o Kong não roteia esse prefixo); a chain `@Order(2)` cobre o restante das rotas, um resource server OAuth2 RS256 que valida o JWT de usuário emitido pelo `api-auth` via JWKS (`app.jwt.jwk-set-uri`).
- **RBAC por ownership, não por permissão** (`RbacAuthorizationService`), diferente de serviços com RBAC baseado em papéis/permissões — aqui a autorização é sempre "o usuário autenticado é dono/participante do recurso?", resolvida a partir da claim `sub` (`authId`) do JWT de usuário. `requireParticipant` cobre tanto conversas usuário-a-usuário (remetente ou destinatário) quanto conversas com o chatbot (o próprio usuário).
- **Duas origens para o mesmo agregado de mensagem** (`MessageService`), mensagens humanas entram por `POST /messaging/messages` (autenticado por JWT de usuário, valida participação na conversa), enquanto respostas do chatbot entram por `POST /internal/messages` (autenticado por token de serviço, chamado pelo pipeline de LLM) — o serviço rejeita explicitamente `messageType=CHATBOT_TO_USER` vindo da rota de usuário, forçando essa origem a passar sempre pela rota interna.
- **Observabilidade de LLM como cidadão de primeira classe** (`LlmObservabilityController`/`LlmObservabilityService`, coleção `llm_observability`), cada etapa do pipeline de IA (`LLM_CALL` ou `TOOL_CALL`) é registrada com nó (`node`), ordem do passo, modelo usado, tokens de entrada/saída/total, latência e status de erro — dados publicados via `POST /internal/observability` e consultáveis por `conversationId`, `node` ou `status`. `MessageMetadata` (campos `specialistsUsed`, `workflowSteps`, `turnId`) reforça que o pipeline de IA é multi-agente/multi-etapa.
- **Integração com o `ai-assistant`**, as rotas `/internal/**` (mensagens do chatbot, observabilidade e emissão de token de serviço) existem para serem consumidas por outro serviço da organização, não por um cliente humano — pelo desenho (payload de observabilidade por `node`/modelo/tokens e metadados de `MessageMetadata`), o consumidor natural é o `ai-assistant`, o serviço responsável pelo pipeline de IA da Solaria. {a confirmar: não há chamada HTTP de saída do `api-messenger` para o `ai-assistant` no código-fonte — a integração é sempre iniciada pelo `ai-assistant` chamando o `api-messenger`}.
- **Tratamento de erro centralizado em `ProblemDetail`** (`GlobalExceptionHandler`, `ProblemDetailFactory`, `ProblemDetailAuthenticationEntryPoint`, `ProblemDetailAccessDeniedHandler`), todas as exceptions de domínio (`ResourceNotFoundException`, `DuplicateResourceException`, `UnauthorizedAccessException`, `ServiceAuthException`, etc.) e as falhas de autenticação/autorização das duas `SecurityFilterChain` respondem no formato RFC 7807, sem vazar mensagem/stacktrace (`spring.web.error.include-*=never` em `application.properties`).
- **Índices explícitos no MongoDB** (anotações `@Indexed`), `senderId`/`receiverId` em `Conversation`, `conversationId` em `Message`, `conversationId`/`node` em `LlmObservability` — `spring.data.mongodb.auto-index-creation=true` garante que os índices sejam criados no boot a partir dessas anotações, e `spring.mongodb.representation.uuid=standard` (mais o parâmetro `uuidRepresentation=standard` na URI) fixa a representação de UUID como `STANDARD` para compatibilizar os `UUID` do Java com o BSON do Mongo.

## Endpoints expostos

### `/messaging/**` — autenticado por JWT de usuário (`api-auth`)

- `POST /messaging/conversations/user-conversations`, cria uma conversa usuário-a-usuário.
- `POST /messaging/conversations/chatbot-conversations`, cria uma conversa usuário-a-chatbot.
- `GET /messaging/conversations/{id}`, busca uma conversa por id (exige ser participante).
- `GET /messaging/conversations/me`, lista as conversas do usuário autenticado.
- `POST /messaging/messages`, envia uma mensagem humana em uma conversa existente.
- `GET /messaging/messages/conversation/{conversationId}`, lista as mensagens de uma conversa (exige ser participante).
- `GET /messaging/updates?since={versão}&limit={n}`, catch-up de mensagens. Devolve o estado atual das mensagens com `changeVersion` maior que `since`. Entram as conversas que o usuário lê hoje e as que ele perdeu depois de `since`. Nas conversas perdidas, só vão as mensagens até a versão da saída. A resposta é `{ syncEpoch, cursor, hasMore, messages }`. Sem `since`, devolve só o ponto de partida.
- `POST /messaging/ratings`, `GET /messaging/ratings/{id}`, `GET /messaging/ratings/evaluated/{evaluatedId}`, `GET /messaging/ratings/evaluator/{evaluatorId}`, `PATCH /messaging/ratings/{id}`, `PATCH /messaging/ratings/{id}/status`, ciclo de vida de avaliações (`RatingType.PROFISSIONAL`/`PRODUTO`) entre avaliador e avaliado.

### `/internal/**` — sem autenticação de aplicação, isolado pela rede

- `POST /internal/messages`, ingestão de mensagens do chatbot (`CHATBOT_TO_USER`) publicadas pelo pipeline de LLM.
- `POST /internal/observability`, ingestão de um passo de observabilidade (`LLM_CALL`/`TOOL_CALL`).
- `GET /internal/observability/{id}`, `GET /internal/observability`, consulta de observabilidade por id ou por filtro (`conversationId`, `node`, `status`).

### Tempo real (STOMP)

O broker é o RabbitMQ em modo relay, e os destinos usam `.` como separador.

- `/topic/users.{userId}.events` leva os eventos duráveis do usuário. Só o dono assina. Recebe `MESSAGE_CREATED`, `MESSAGE_EDITED` e `MESSAGE_DELETED` de todas as conversas em que ele participa, inclusive das mensagens que ele mesmo enviou. Recebe também `CONVERSATION_UPDATED` e `CONVERSATION_REMOVED`. O payload de `MESSAGE_*` é o `MessageResponseDTO`, com `changeVersion`.
- `/topic/users.{userId}.acks` e `/topic/users.{userId}.errors` levam a confirmação do envio e os erros. Só o dono assina.
- `/topic/conversations.{id}` leva presença, digitação e `PRESENCE_SNAPSHOT`. Só participantes assinam. O tópico não leva mais `MESSAGE_*`.

O cliente assina `.events` antes de chamar `GET /messaging/updates`. O tópico `/topic/users.{userId}.conversations` não existe mais.

```Tree do Repositório
├── .github/
│   ├── CODEOWNERS
│   ├── CONTRIBUTING.md
│   ├── pull_request_template.md
│   └── workflows/
│       ├── ci.yml
│       ├── quality.yml
│       ├── release.yml
│       └── sonarqube.yml
├── .mvn/
├── src/
│   ├── main/
│   │   ├── java/com/solaria/messenger/
│   │   │   ├── MessagerApplication.java
│   │   │   ├── core/                      # regra de negócio, sem dependência de rest/ ou websocket/
│   │   │   │   ├── contract/              # DTOs de entrada/saída (request, response)
│   │   │   │   ├── exception/
│   │   │   │   ├── integration/           # clientes: auth/, core/ (api-core), user/
│   │   │   │   ├── mapper/
│   │   │   │   ├── model/                 # documents Mongo (+ enums)
│   │   │   │   ├── observability/
│   │   │   │   ├── repository/
│   │   │   │   ├── security/              # jwt/, rbac/
│   │   │   │   └── service/               # casos de uso; service/port = portas de saída
│   │   │   ├── rest/                      # adaptador HTTP (controller, openapi, error, security, dto)
│   │   │   └── websocket/                 # adaptador STOMP (config, controller, presence, publish, session, security)
│   │   └── resources/
│   │       └── application.properties
│   └── test/
│       ├── java/com/solaria/messenger/
│       └── resources/
├── .dockerignore
├── .editorconfig
├── .env.example
├── Dockerfile
├── LICENSE
├── README.md
├── ARCHITECTURE.md
├── RUNNING.md
├── mvnw
├── mvnw.cmd
├── pom.xml
└── sonar-project.properties
```

## Divisão em `core`, `rest` e `websocket`

O código segue o estilo de portas e adaptadores: `core/` concentra modelo, repositórios e casos de uso e **não importa** `rest/` nem `websocket/`. `rest/` e `websocket/` são adaptadores de entrada que chamam os serviços de `core/`; o `websocket/` implementa as portas de saída em `core/service/port` (publicação de eventos e revogação de sessão).

Referências consultadas:
- [Hexagonal architecture: ports and adapters (Netguru)](https://www.netguru.com/blog/hexagonal-architecture-ports-adapters-guide)
- [Designing Hexagonal Architecture with Java Spring Boot](https://github.com/educostadev/Designing-Hexagonal-Architecture-with-Java-SpringBoot)
- [Spring Modulith — fundamentals](https://docs.spring.io/spring-modulith/reference/2.2-SNAPSHOT/fundamentals.html)
- [Spring WebSocket messaging architectures](https://spring.io/blog/2013/07/24/spring-framework-4-0-m2-websocket-messaging-architectures)
