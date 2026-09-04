# Arquitetura do Repositório

O `api-messenger` segue uma arquitetura em camadas típica de um serviço Spring Boot (`controller` → `service` → `repository` → `model`), sem separação `service`/`service.impl` — as classes de serviço concentram a regra de negócio diretamente. Os `controller` implementam uma interface `openapi` dedicada por recurso (`ChatbotMessageOpenApi`, `ConversationOpenApi`, `MessageOpenApi`, `RatingOpenApi`, `LlmObservabilityOpenApi`), usada só para anotações do Springdoc e manter a classe do controller limpa. A persistência é 100% sobre MongoDB (`spring-boot-starter-data-mongodb`), sem banco relacional — cada agregado (`Conversation`, `Message`, `Rating`, `LlmObservability`) é um `@Document` próprio, sem transação distribuída entre eles.

<p>
  <a href="https://github.com/syvixor/skills-icons">
    <img src="https://skills.syvixor.com/api/icons?i=java,springboot,mongodb" height="48" alt="Arquitetura do api-messenger">
  </a>
</p>

- **Duas `SecurityFilterChain` independentes e segregadas por `@Order`** (`SecurityConfig`), documentado no próprio código-fonte: a chain `@Order(1)` cobre só `/internal/**`, autentica via token de serviço M2M HS256 (`ServiceTokenAuthFilter` + `ServiceTokenProvider`, segredo compartilhado `SERVICE_JWT_SECRET`); a chain `@Order(2)` cobre o restante das rotas, um resource server OAuth2 RS256 clássico que valida o JWT de usuário emitido pelo `api-auth` via JWKS (`app.jwt.jwk-set-uri`). O mesmo padrão de duas chains é usado no `api-persistence`, segundo o comentário da classe.
- **Emissão de token de serviço própria** (`ServiceAuthController`, `ServiceTokenProvider`, `ServiceTokenProperties`), o serviço não delega a emissão do token M2M a nenhum outro repositório: `POST /internal/service-tokens` minta um par access+refresh token a partir de um `clientSecret` compartilhado (`SERVICE_CLIENT_SECRET`), comparado em tempo constante (`MessageDigest.isEqual`); `POST /internal/service-tokens/refresh` troca um refresh token válido por um novo access token.
- **RBAC por ownership, não por permissão** (`RbacAuthorizationService`), diferente de serviços com RBAC baseado em papéis/permissões — aqui a autorização é sempre "o usuário autenticado é dono/participante do recurso?", resolvida a partir da claim `sub` (`authId`) do JWT de usuário. `requireParticipant` cobre tanto conversas usuário-a-usuário (remetente ou destinatário) quanto conversas com o chatbot (o próprio usuário).
- **Duas origens para o mesmo agregado de mensagem** (`MessageService`), mensagens humanas entram por `POST /messaging/messages` (autenticado por JWT de usuário, valida participação na conversa), enquanto respostas do chatbot entram por `POST /internal/messages` (autenticado por token de serviço, chamado pelo pipeline de LLM) — o serviço rejeita explicitamente `messageType=CHATBOT_TO_USER` vindo da rota de usuário, forçando essa origem a passar sempre pela rota interna.
- **Observabilidade de LLM como cidadão de primeira classe** (`LlmObservabilityController`/`LlmObservabilityService`, coleção `llm_observability`), cada etapa do pipeline de IA (`LLM_CALL` ou `TOOL_CALL`) é registrada com nó (`node`), ordem do passo, modelo usado, tokens de entrada/saída/total, latência e status de erro — dados publicados via `POST /internal/observability` e consultáveis por `conversationId`, `node` ou `status`. `MessageMetadata` (campos `specialistsUsed`, `workflowSteps`, `turnId`) reforça que o pipeline de IA é multi-agente/multi-etapa.
- **Integração com o `ai-assistant`**, as rotas `/internal/**` (mensagens do chatbot, observabilidade e emissão de token de serviço) existem para serem consumidas por outro serviço da organização, não por um cliente humano — pelo desenho (payload de observabilidade por `node`/modelo/tokens e metadados de `MessageMetadata`), o consumidor natural é o `ai-assistant`, o serviço responsável pelo pipeline de IA da Solaria. {a confirmar: não há chamada HTTP de saída do `api-messenger` para o `ai-assistant` no código-fonte — a integração é sempre iniciada pelo `ai-assistant` chamando o `api-messenger`}.
- **Tratamento de erro centralizado em `ProblemDetail`** (`GlobalExceptionHandler`, `ProblemDetailFactory`, `ProblemDetailAuthenticationEntryPoint`, `ProblemDetailAccessDeniedHandler`), todas as exceptions de domínio (`ResourceNotFoundException`, `DuplicateResourceException`, `UnauthorizedAccessException`, `ServiceAuthException`, etc.) e as falhas de autenticação/autorização das duas `SecurityFilterChain` respondem no formato RFC 7807, sem vazar mensagem/stacktrace (`spring.web.error.include-*=never` em `application.properties`).
- **Índices explícitos no MongoDB** (`MongoConfig` + anotações `@Indexed`), `senderId`/`receiverId` em `Conversation`, `conversationId` em `Message`, `conversationId`/`node` em `LlmObservability` — `spring.data.mongodb.auto-index-creation=true` garante que os índices sejam criados no boot a partir dessas anotações, e `MongoConfig` fixa a representação de UUID como `STANDARD` para compatibilizar os `UUID` do Java com o BSON do Mongo.

## Endpoints expostos

### `/messaging/**` — autenticado por JWT de usuário (`api-auth`)

- `POST /messaging/conversations/user-conversations`, cria uma conversa usuário-a-usuário.
- `POST /messaging/conversations/chatbot-conversations`, cria uma conversa usuário-a-chatbot.
- `GET /messaging/conversations/{id}`, busca uma conversa por id (exige ser participante).
- `GET /messaging/conversations/me`, lista as conversas do usuário autenticado.
- `POST /messaging/messages`, envia uma mensagem humana em uma conversa existente.
- `GET /messaging/messages/conversation/{conversationId}`, lista as mensagens de uma conversa (exige ser participante).
- `POST /messaging/ratings`, `GET /messaging/ratings/{id}`, `GET /messaging/ratings/evaluated/{evaluatedId}`, `GET /messaging/ratings/evaluator/{evaluatorId}`, `PATCH /messaging/ratings/{id}`, `PATCH /messaging/ratings/{id}/status`, ciclo de vida de avaliações (`RatingType.PROFISSIONAL`/`PRODUTO`) entre avaliador e avaliado.

### `/internal/**` — autenticado por token de serviço M2M

- `POST /internal/service-tokens`, minta um par access+refresh token de serviço a partir de `clientSecret`.
- `POST /internal/service-tokens/refresh`, troca um refresh token válido por um novo access token.
- `POST /internal/messages`, ingestão de mensagens do chatbot (`CHATBOT_TO_USER`) publicadas pelo pipeline de LLM.
- `POST /internal/observability`, ingestão de um passo de observabilidade (`LLM_CALL`/`TOOL_CALL`).
- `GET /internal/observability/{id}`, `GET /internal/observability`, consulta de observabilidade por id ou por filtro (`conversationId`, `node`, `status`).

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
│   │   │   ├── config/
│   │   │   │   ├── MongoConfig.java
│   │   │   │   └── OpenApiConfig.java
│   │   │   ├── controller/
│   │   │   │   ├── ChatbotMessageController.java
│   │   │   │   ├── ConversationController.java
│   │   │   │   ├── LlmObservabilityController.java
│   │   │   │   ├── MessageController.java
│   │   │   │   ├── RatingController.java
│   │   │   │   └── ServiceAuthController.java
│   │   │   ├── dto/
│   │   │   │   ├── request/
│   │   │   │   └── response/
│   │   │   ├── exception/
│   │   │   │   └── handler/
│   │   │   ├── model/
│   │   │   │   └── enums/
│   │   │   ├── openapi/
│   │   │   ├── repository/
│   │   │   ├── security/
│   │   │   │   ├── rbac/
│   │   │   │   └── service/
│   │   │   └── service/
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
