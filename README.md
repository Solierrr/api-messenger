# Finalidade do repositório

O `api-messenger` é o serviço de mensageria da Solaria, responsável por armazenar e servir todo o histórico de conversas trocadas na plataforma — tanto conversas usuário-a-usuário quanto conversas usuário-a-chatbot atendidas pelo pipeline de LLM. O serviço modela conversas e mensagens sobre MongoDB, expõe endpoints protegidos por JWT de usuário (emitido pelo `api-auth`) para o fluxo humano, e uma rota interna autenticada por token de serviço M2M para que o pipeline de IA (`ai-assistant`) publique respostas do chatbot e registre observabilidade de cada chamada de LLM/ferramenta (tokens consumidos, latência, especialistas acionados). Também centraliza avaliações (ratings) de profissionais e produtos vinculadas às conversas.

<p>

[![License](https://img.shields.io/github/license/Solierrr/api-messenger)](https://github.com/Solierrr/api-messenger/blob/main/LICENSE)
[![GitHub Last Commit](https://img.shields.io/github/last-commit/Solierrr/api-messenger)](https://github.com/Solierrr/api-messenger/commits)
[![GitHub Issues](https://img.shields.io/github/issues/Solierrr/api-messenger)](https://github.com/Solierrr/api-messenger/issues)
[![GitHub Pull Requests](https://img.shields.io/github/issues-pr/Solierrr/api-messenger)](https://github.com/Solierrr/api-messenger/pulls)
[![GitHub Contributors](https://img.shields.io/github/contributors/Solierrr/api-messenger)](https://github.com/Solierrr/api-messenger/graphs/contributors)
[![Release](https://img.shields.io/github/v/release/Solierrr/api-messenger)](https://github.com/Solierrr/api-messenger/releases)

</p>

<div align="center">

<p>
  <a href="https://github.com/syvixor/skills-icons">
    <img src="https://skills.syvixor.com/api/icons?i=java,springboot,spring,mongodb,swagger" height="48" alt="Stack do api-messenger">
  </a>
</p>

<p>

[![Java](https://img.shields.io/badge/Java_21-437291?logo=openjdk&logoColor=white)](https://openjdk.org/projects/jdk/21/)
[![Spring Boot](https://img.shields.io/badge/Spring_Boot-6DB33F?logo=springboot&logoColor=white)](https://spring.io/projects/spring-boot)
[![Spring Security](https://img.shields.io/badge/Spring_Security-6DB33F?logo=springsecurity&logoColor=white)](https://spring.io/projects/spring-security)
[![MongoDB](https://img.shields.io/badge/MongoDB-47A248?logo=mongodb&logoColor=white)](https://www.mongodb.com/)
[![Swagger](https://img.shields.io/badge/Swagger-85EA2D?logo=swagger&logoColor=black)](https://swagger.io/)

</p>

</div>

## Aprofunde-se no Projeto!

- [ARCHITECTURE.md](./ARCHITECTURE.md), camadas do serviço, modelagem no MongoDB, endpoints internos/públicos e árvore real do repositório.
- [RUNNING.md](./RUNNING.md), como subir o `api-messenger` localmente e impedimentos conhecidos.
- [DEPLOYMENT.md](https://github.com/Solierrr/.github/blob/main/.github/DEPLOYMENT.md), pipeline de deploy padrão da organização (`main`/`qa`, Docker Hub, ArgoCD).

## Contribuindo

- [CONTRIBUTING.md](./.github/CONTRIBUTING.md), convenções de commit, branch e Pull Request.
- {a confirmar: CODE_OF_CONDUCT.md e SECURITY.md ainda não existem neste repositório — ver se devem ser adicionados ou linkados a partir do repositório `.github` compartilhado da organização}
