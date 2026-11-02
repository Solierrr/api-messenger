<p align="center">
  <img src="../assets/readme/banners/springboot.png" alt="Spring Boot" width="100%">
</p>

# Executando localmente

O passo a passo comum dos serviços Java está em [JAVA.md](https://github.com/Solierrr/docs-warehouse/blob/main/readme/running/JAVA.md). Aqui fica só o que é deste serviço.

## Pré-requisitos

- Java 21.
- MongoDB com replica set. Sem replica set, criar conversa e adicionar participantes falham, porque essas operações usam transação.
- `api-auth` rodando na porta 8081, para validar o JWT e consultar a conta. Sem ele, as rotas `/messaging/**` falham.
- Infisical CLI logado, para gerar o `.env`.

São opcionais:

- Redis, para presença, revogação entre instâncias e a confirmação de usuário ativo. Sem ele, cada instância usa só a própria visão e consulta o `api-auth` em toda requisição.
- RabbitMQ 4 com o plugin STOMP, só para testar mais de uma instância. A imagem está no [`infra-broker`](https://github.com/Solierrr/infra-broker).
- `api-core`, só para criar comunidades.
- `api-notify`, só para testar o aviso no celular.

O `api-core` e este serviço usam a porta 8080 por padrão. Rode um de cada vez ou mude a porta de um deles.

## Variáveis

`make vault` grava o `.env` com as pastas `/database`, `/auth`, `/service-urls` e `/otel` do Infisical. O [`.env.example`](https://github.com/Solierrr/api-messenger/blob/main/.env.example) lista também as pastas `/broker` e `/redis`, que ficam fora do `make vault` e precisam ser preenchidas à mão quando usadas.

Sem `.env`, o serviço sobe com os padrões locais, ou seja, Mongo em `localhost:27017`, banco `solaria_messenger_db`, broker em memória, Redis em `localhost:6379` e push desligado.

| Variável | Efeito |
|---|---|
| `RABBITMQ_HOST` | liga o relay para o RabbitMQ. `RABBITMQ_RELAY_ENABLED=false` força o broker em memória |
| `NOTIFY_BASE_URL` | liga o aviso no celular pelo `api-notify` |
| `MESSAGES_MUTATION_WINDOW` | prazo para editar e apagar mensagem, em duração ISO 8601. O padrão é `PT30M` |
| `DEBUG_ADMIN_SUBS` | `sub` dos usuários que acessam o histórico administrativo, separados por vírgula |
| `SOLARIA_LANGSMITH_API_KEY`, `SOLARIA_LANGSMITH_PROJECT` | ligam o resumo do LangSmith. Sem elas, a rota responde `enabled: false` |

## Comandos

| Comando | O que faz |
|---|---|
| `make dev` | sobe o serviço na porta 8080 |
| `make test` | roda os testes (precisa de Mongo e Redis locais, como no CI) |
| `make lint` | roda o Checkstyle, como no CI |
| `make vault ENV=local` | gera o `.env` a partir do Infisical |

## Testando uma chamada

- Swagger em http://localhost:8080/swagger-ui.html. No perfil `dev`, `POST /dev/login` devolve um JWT para colar no Authorize.
- Coleção Bruno na pasta [`http/`](https://github.com/Solierrr/api-messenger/blob/main/http/README.md) do repositório, com conversas, mensagens, comunidades, avaliações, rotas internas e telemetria.
- `GET /actuator/health/readiness` responde `UP` quando o Mongo está acessível.
- Para subir o ecossistema local inteiro em Kubernetes, com duas instâncias deste serviço, RabbitMQ, Mongo e Redis, use o `infra-local/solier.ps1` e o manifesto `k8s-manifests/30-api-messenger.yaml` da pasta de trabalho local.

## Problemas conhecidos

- Sem coletor OpenTelemetry local, o log mostra erros de exportação para `localhost:4318`. Eles não afetam o serviço.
- Sem o `api-core` no ar, criar comunidade responde 503 `DEPENDENCY_UNAVAILABLE`. As outras rotas não dependem dele.
