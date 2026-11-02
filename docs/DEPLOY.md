<p align="center">
  <img src="../assets/readme/banners/render.png" alt="Render" width="100%">
</p>

# Deploy

O processo comum está em [QA_RENDER.md](https://github.com/Solierrr/docs-warehouse/blob/main/readme/deploy/QA_RENDER.md) e em [PROD_GKE_ARGOCD.md](https://github.com/Solierrr/docs-warehouse/blob/main/readme/deploy/PROD_GKE_ARGOCD.md). Aqui fica só o que é deste serviço.

## Ambientes

| Ambiente | Onde | Endereço |
|---|---|---|
| Local | máquina de quem desenvolve | http://localhost:8080 |
| QA | Render, branch `qa`, uma instância | https://api-messenger-5g2n.onrender.com |
| Produção | GKE (`solaria-gke`, `us-central1-a`), pelo Argo CD | manifestos no `infra-gitops` |

## Particularidades

- No Render, o `healthCheckPath` deve ser `/actuator/health/readiness`. O pronto confere só o Mongo, então uma queda do Redis ou do RabbitMQ não derruba o deploy.
- O QA roda uma instância só, com o broker em memória. Com mais de uma instância, o relay para o RabbitMQ e o Redis precisam estar configurados, senão cada instância só entrega eventos às próprias sessões.
- O serviço lê os segredos do Infisical na partida, pelo `entrypoint.sh`, quando recebe `INFISICAL_CLIENT_ID` e `INFISICAL_CLIENT_SECRET`. O ambiente padrão é `qa`, e `INFISICAL_ENV` muda isso.
- O banco precisa ser replica set, como o MongoDB Atlas, por causa da transação de admissão.
- O Kong publica o prefixo `/messaging` na porta 8080 com `strip-path: false` e os plugins `cors` e `rate-limiting-persistence`. O WebSocket passa por `/messaging/ws`. O prefixo `/internal` não é publicado.
- O workflow `infisical-env-check` confere todo dia se as variáveis do `.env.example` existem no Infisical.
