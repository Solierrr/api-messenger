<p align="center">
  <img src="../assets/readme/banners/swagger.png" alt="Swagger" width="100%">
</p>

# API

O contrato fica no [`openapi.yaml`](https://github.com/Solierrr/api-messenger/blob/main/openapi.yaml) do repositório e, com o serviço no ar, no Swagger em `/swagger-ui.html`. O arquivo está atrás do código. Ele não traz o estado pessoal, a troca de gestor, a edição e a exclusão de mensagem nem o NPS, e o Swagger gerado pelo código é a referência. As rotas `/messaging/admin/**` ficam fora dos dois. O tempo real está em [REALTIME.md](./REALTIME.md).

## Autenticação

- As rotas `/messaging/**` exigem o JWT de acesso do `api-auth` em `Authorization: Bearer`, com conta ativa. A conta é consultada no `api-auth` a cada requisição.
- As rotas `/messaging/admin/**` exigem também que o `sub` do JWT esteja em `DEBUG_ADMIN_SUBS`.
- As rotas `/internal/**` ainda não têm autenticação de aplicação e hoje dependem de o Kong não rotear esse prefixo. O padrão definido exige a `X-Service-Key` de cada chamador nessas rotas, adequação que está pendente.
- O WebSocket autentica no CONNECT STOMP, com o mesmo JWT.

## Grupos de rotas

| Grupo | Prefixo | Para que serve |
|---|---|---|
| Conversas | `/messaging/conversations` | criar diretas, grupos e conversas com o assistente, listar as conversas da pessoa, alterar leitura, silêncio e arquivo, e gerir participantes e gestor de um grupo |
| Mensagens | `/messaging/messages` | enviar, editar, apagar e paginar o histórico de uma conversa |
| Catch-up | `/messaging/updates` | buscar as mensagens que mudaram depois da última versão que o cliente viu |
| Comunidades | `/messaging/communities` | criar a comunidade de um projeto, arquivar e reativar, gerir membros e papéis, e criar ou listar subgrupos |
| Avaliações | `/messaging/ratings` | criar, consultar, editar e mudar o status de avaliações de profissionais e produtos |
| NPS | `/messaging/nps` | registrar a nota de uma conversa com o assistente |
| Histórico administrativo | `/messaging/admin` | listar e detalhar as conversas com o assistente e resumir o uso no LangSmith, para o painel do `web-sandbox` |
| Interno | `/internal/messages`, `/internal/observability` | o assistente grava a resposta dele e registra cada etapa do pipeline de LLM |
| Desenvolvimento | `/dev/login` | só no perfil `dev`, repassa o login ao `api-auth` para obter um JWT pelo Swagger |

As regras de cada grupo estão em [OVERVIEW.md](./OVERVIEW.md) e nos [fluxos](../flows/).

## Limites

- A mensagem tem até 8000 caracteres. O título de grupo tem até 120.
- O histórico e o catch-up devolvem de 1 a 500 mensagens por página, com 100 por padrão. Um valor fora da faixa é ajustado, sem erro.
- O histórico aceita um cursor só, `sinceSequence` ou `beforeSequence`, e devolve sempre em ordem crescente de `sequence`.
- A avaliação vai de 0.0 a 5.0, com comentário de até 1000 caracteres. O NPS vai de 0 a 10.
- A busca de telemetria aceita no máximo um filtro e, sem filtro, devolve os 100 registros mais recentes.
- O histórico administrativo olha no máximo 2000 conversas do período, que por padrão são os últimos 7 dias, e marca `truncated: true` quando passa disso. O resumo do LangSmith fica em cache por 2 minutos, o mesmo para todas as instâncias.

## Erros

Os erros seguem o [contrato de erro](https://github.com/Solierrr/docs-warehouse/blob/main/docs/standards/ERRORS.md), com `errorCode`, `timestamp` e `traceId`. O serviço ainda usa códigos próprios no lugar dos genéricos do [catálogo](https://github.com/Solierrr/docs-warehouse/blob/main/docs/standards/ERROR_CODES.md), como `VALIDATION_ERROR` com 400 no lugar de `VALIDATION_FAILED` com 422, `UNAUTHORIZED_ACCESS` no lugar de `FORBIDDEN` e `RESOURCE_NOT_FOUND` no lugar de `NOT_FOUND`.

Códigos específicos:

| HTTP | `errorCode` | Quando |
|---|---|---|
| 409 | `CONCURRENT_MODIFICATION` | outra operação mudou o mesmo recurso e as tentativas acabaram |
| 409 | `DATA_INTEGRITY_VIOLATION` | um índice único recusou a escrita |
| 409 | `DUPLICATE_RESOURCE` | o projeto já tem comunidade ou a conversa já tem NPS |
| 409 | `DIRECT_IDENTITY_INCONSISTENT` | a conversa direta encontrada não corresponde ao par |
| 409 | `GROUP_MANAGER_UNAVAILABLE` | o grupo não tem um gestor válido para a operação |
| 409 | `SUCCESSOR_REQUIRED`, `SUCCESSOR_INELIGIBLE` | o gestor saiu sem indicar sucessor, ou o indicado não pode assumir |
| 409 | `MEMBERSHIP_CLEANUP_REQUIRED` | quem volta à comunidade ainda está em algum subgrupo |
| 422 | `BUSINESS_RULE_VIOLATION` | regra de negócio, como prazo de edição vencido, comunidade arquivada ou nenhum convidado aceito. Na lista de conversas, um estado pessoal inválido em qualquer conversa faz a lista inteira responder 422 |
| 503 | `DEPENDENCY_UNAVAILABLE` | `api-auth`, `api-core` ou Mongo fora do ar ou com resposta inválida |
| 503 | `MESSAGE_PERSISTENCE_UNCERTAIN`, `MESSAGE_MUTATION_UNCERTAIN`, `CONVERSATION_PERSISTENCE_UNCERTAIN`, `ADMISSION_PERSISTENCE_UNCERTAIN` | não se sabe se a escrita aconteceu. Consulte o recurso antes de repetir |
| 503 | `REMOVAL_INCOMPLETE` | a remoção de um membro da comunidade parou no meio. Repetir o pedido continua de onde parou |

No STOMP, as recusas de frame usam `SEND_NOT_ALLOWED`, `SUBSCRIBE_NOT_ALLOWED`, `SUBSCRIBE_DESTINATION_REQUIRED` e `COMMAND_NOT_ALLOWED`, e a quinta sessão recebe `SESSION_LIMIT_EXCEEDED`.

## Exemplos

Envio de mensagem num grupo.

```http
POST /messaging/messages
Authorization: Bearer <JWT de acesso>
Content-Type: application/json

{
  "conversationId": "66fb99e8fa4ef1b28ad72fc1",
  "messageType": "USER_TO_GROUP",
  "content": "A visita técnica ficou para quinta às 9h.",
  "clientMessageId": "1f0c7a2e-5b3d-4e8a-9c61-2a7d4b9e0f13"
}
```

A resposta é `201` com a mensagem gravada, incluindo `sequence` e `changeVersion`. Os participantes recebem `MESSAGE_CREATED` no tópico de eventos de cada um.

Catch-up depois de reconectar.

```http
GET /messaging/updates?since=48211&limit=100
Authorization: Bearer <JWT de acesso>
```

A resposta traz `syncEpoch`, `cursor`, `hasMore` e as mensagens que mudaram depois da versão 48211, em ordem crescente de `changeVersion`. O cliente guarda o `cursor` como próximo `since`. Sem `since`, a resposta traz só o ponto de partida.
