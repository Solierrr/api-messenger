<p align="center">
  <img src="../assets/readme/banners/springboot.png" alt="Spring Boot" width="100%">
</p>

# Situação

Levantamento feito na `main` do repositório.

## Funciona

- Conversas diretas, grupos, conversas com o assistente, gestão de grupo, estado pessoal e lista de conversas com marcadores de sincronização (2026-10-31).
- Comunidades de projeto com papéis, subgrupos e remoção em cascata. O administrador promove membros e arquiva, e só o dono rebaixa ou remove administradores (2026-10-31).
- Convidados aceitos em parte nas cinco operações com convidados, com os recusados e o motivo na resposta (2026-10-31).
- Grupo que fica vazio é apagado com as mensagens e os estados pessoais (2026-10-31).
- Envio, edição e exclusão de mensagens, com prazo de edição e exclusão (2026-10-31).
- Tempo real com eventos de mensagem no tópico de cada usuário e catch-up por `GET /messaging/updates` (2026-10-31).
- Mais de uma instância com relay para o RabbitMQ e presença no Redis, ou uma instância com o broker em memória (2026-10-31).
- Aviso no celular para quem está desconectado, pelo `api-notify`, quando `NOTIFY_BASE_URL` está configurada (2026-10-31).
- Histórico administrativo das conversas com o assistente e resumo do LangSmith com cache compartilhado entre as instâncias, para o painel do `web-sandbox` (2026-10-31).
- Confirmação de usuário ativo guardada no Redis por 30 s, para todas as instâncias (2026-10-31).
- Pronto do Mongo restrito ao banco do serviço, no padrão de health checks (2026-10-31).

## Falta

- O `openapi.yaml` não traz o estado pessoal, a troca de gestor, a edição e a exclusão de mensagem nem o NPS (2026-10-31).
- Os códigos de erro genéricos ainda não seguem o catálogo da organização, e os específicos não estão nele (2026-10-31).
- O `README.md`, o `docs/ARCHITECTURE.md` e o `docs/RUNNING.md` do repositório ainda descrevem a estrutura antiga, com serviços no lugar dos casos de uso, a rota `/messaging/conversations/user-conversations` e o tópico `.conversations` (2026-10-31).
- Nenhum cliente chama as rotas de avaliação nem de NPS, segundo o levantamento de 2026-10-29.
- A comunidade é criada e mantida por pedidos ao próprio serviço. Ela não acompanha a equipe do projeto no `api-core`.
- O aviso de mensagem nova para quem está desconectado é só por push, e o aviso por e-mail, já definido como canal, ainda não foi implementado.

## Depende de outro repositório

- Criar comunidade depende de `GET /internal/technical-projects/{projectId}/requester-access/{authId}` no `api-core` (2026-10-31).
- O aviso no celular depende do `api-notify` aceitar a chave `SERVICE_KEY_API_MESSENGER` (2026-10-31).
- As rotas `/internal/**` ainda dependem de o Kong não publicar esse prefixo, e a adoção da `X-Service-Key` por chamador está pendente (2026-10-31).
