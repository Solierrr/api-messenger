<p align="center">
  <img src="./assets/readme/banners/springboot.png" alt="Spring Boot" width="100%">
</p>

<p align="center">
  <img src="./assets/readme/stacks/java.png" width="100" hspace="8" alt="Java" title="Java">
  <img src="./assets/readme/stacks/spring-security.png" width="100" hspace="8" alt="Spring Security" title="Spring Security">
  <img src="./assets/readme/stacks/jwt.png" width="100" hspace="8" alt="JWT" title="JWT">
  <img src="./assets/readme/stacks/mongodb.png" width="100" hspace="8" alt="MongoDB" title="MongoDB">
  <img src="./assets/readme/stacks/redis.png" width="100" hspace="8" alt="Redis" title="Redis">
  <img src="./assets/readme/stacks/upstash.png" width="100" hspace="8" alt="Upstash" title="Upstash">
  <img src="./assets/readme/stacks/swagger.png" width="100" hspace="8" alt="Swagger" title="Swagger">
  <img src="./assets/readme/stacks/bruno.png" width="100" hspace="8" alt="Bruno" title="Bruno">
</p>

# Central de mensagens

[![License](https://img.shields.io/github/license/Solierrr/api-messenger)](https://github.com/Solierrr/api-messenger/blob/main/LICENSE)
[![GitHub Last Commit](https://img.shields.io/github/last-commit/Solierrr/api-messenger)](https://github.com/Solierrr/api-messenger/commits)
[![GitHub Pull Requests](https://img.shields.io/github/issues-pr/Solierrr/api-messenger)](https://github.com/Solierrr/api-messenger/pulls)
[![Release](https://img.shields.io/github/v/release/Solierrr/api-messenger)](https://github.com/Solierrr/api-messenger/releases)

Este serviço guarda e entrega as conversas da plataforma. Clientes, profissionais e empresas conversam sem sair dela, em conversas individuais, em grupos e nos subgrupos da comunidade de cada projeto. Qualquer pessoa pode iniciar uma conversa com outra a qualquer momento. O serviço também guarda as conversas de cada pessoa com o assistente virtual, a nota que ela dá ao atendimento e o registro de uso dos modelos de linguagem, que a equipe consulta num painel interno.

As mensagens chegam em tempo real por WebSocket, com aviso de quem está conectado e de quem está digitando, e quem está desconectado recebe um aviso no celular pelo serviço de notificações. Quem volta a se conectar busca o que perdeu desde a última vez. O acesso das pessoas é validado pelo token JWT do serviço de acesso, e o cadastro central confirma quem pode criar a comunidade de um projeto. As conversas ficam no MongoDB, o Redis guarda quem está conectado, e o RabbitMQ distribui os eventos entre as instâncias do serviço.

## Aprofunde-se no Projeto!

- [OVERVIEW.md](./docs/OVERVIEW.md), o que o serviço oferece e as regras de negócio.
- [ARCHITECTURE.md](./docs/ARCHITECTURE.md), organização do código, integrações, decisões técnicas e os termos deste serviço explicados em linguagem simples.
- [REALTIME.md](./docs/REALTIME.md), o protocolo de tempo real, com destinos, eventos, presença e revogação de sessões.
- [RUNNING.md](./docs/RUNNING.md), como executar e testar localmente.
- [API.md](./docs/API.md), como autenticar, os grupos de rotas e os erros.
- [DEPLOY.md](./docs/DEPLOY.md), ambientes e publicação.
- [STATUS.md](./docs/STATUS.md), o que funciona, o que falta e as dívidas técnicas.
- [Fluxos](./flows/), as conversas, as mensagens, a comunidade, o tempo real e a resposta do assistente.
- [Banco de dados](./database/), as coleções do MongoDB e as chaves do Redis.

## Contribuindo

- [CONTRIBUTING.md](https://github.com/Solierrr/.github/blob/main/.github/CONTRIBUTING.md), convenções de commit, branch e Pull Request.
- [CODE_OF_CONDUCT.md](https://github.com/Solierrr/.github/blob/main/CODE_OF_CONDUCT.md), código de conduta.
- [SECURITY.md](https://github.com/Solierrr/.github/blob/main/SECURITY.md), como reportar uma vulnerabilidade.
