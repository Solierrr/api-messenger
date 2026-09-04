# Rodando o Projeto Localmente

Este repositório é Java 21 + Spring Boot, buildado com Maven. O processo local é sempre o mesmo: clonar, abrir na IDE, baixar as dependências via `mvnw` e subir a aplicação. O `api-messenger` depende de um MongoDB acessível e, para o fluxo autenticado por usuário, de um `api-auth` no ar expondo JWKS — verifique a seção de impedimentos abaixo antes de iniciar.

<p>
  <a href="https://github.com/syvixor/skills-icons">
    <img src="https://skills.syvixor.com/api/icons?i=java,springboot,spring,github,mongodb" height="48" alt="Rodando o Projeto — api-messenger">
  </a>
</p>

## Possíveis Impedimentos

- **JDK 21 instalado localmente**, a mesma versão usada no [`Dockerfile`](./Dockerfile) do repositório (`eclipse-temurin:21`) — rodar fora do container exige essa versão instalada e configurada como `JAVA_HOME`.
- **MongoDB acessível**, o serviço se conecta via `DB_MONGO_URI` (padrão `mongodb://localhost:27017`) no banco `DB_MONGO_MESSENGER` (padrão `solaria_messenger_db`), ver `spring.data.mongodb.uri` em `application.properties` — sem uma instância local ou remota no ar, a aplicação sobe (não há checagem de conexão no boot), mas qualquer chamada a `/messaging/**` ou `/internal/**` que toque o banco falha. O jeito mais simples de ter um Mongo local é `docker run -d -p 27017:27017 --name mongo-messenger mongo`.
- **Segredo do token de serviço M2M**, `SERVICE_JWT_SECRET` e `SERVICE_CLIENT_SECRET` não têm valor padrão em `application.properties` — sem eles, o `ServiceTokenProperties`/`ServiceTokenProvider` falha ao subir e nenhuma rota `/internal/**` funciona (nem a emissão de token via `POST /internal/service-tokens`, nem a validação feita por `ServiceTokenAuthFilter`).
- **JWKS do `api-auth` para validar JWT de usuário**, `JWT_JWK_SET_URI` aponta por padrão para `http://localhost:8081/.well-known/jwks.json` — sem o `api-auth` no ar nessa porta (ou a URL ajustada para o ambiente correto), qualquer rota `/messaging/**` retorna 401, já que o `NimbusJwtDecoder` não consegue buscar a chave pública para validar a assinatura.
- **Secrets locais**, variáveis de ambiente equivalentes às injetadas em runtime pelo [Infisical](https://infisical.com) precisam ser criadas manualmente em `.env` na raiz do projeto (ver [`.env.example`](./.env.example) para a lista completa) — sem elas, a aplicação sobe mas falha ao tentar se conectar em dependências externas.

## Instalação do Projeto

### Iniciando o repositório com o Github

<p>
  <a href="https://github.com/syvixor/skills-icons">
    <img src="https://skills.syvixor.com/api/icons?i=github,intellij" height="48" alt="Frameworks">
  </a>
</p>

Clone o repositório e abra no IntelliJ IDEA.

```Comandos para clonar o repositório
git clone https://github.com/Solierrr/api-messenger.git
cd ./api-messenger
idea .
```

### Instalando dependências necessárias para rodar o projeto localmente

<p>
  <a href="https://github.com/syvixor/skills-icons">
    <img src="https://skills.syvixor.com/api/icons?i=maven,apache" height="48" alt="Frameworks">
  </a>
</p>

Use sempre o wrapper (`mvnw`/`mvnw.cmd`) em vez de um Maven instalado globalmente, para garantir a mesma versão usada no CI. Antes de rodar, copie `.env.example` para `.env` e preencha as variáveis descritas na seção de impedimentos.

```Comandos para instalação de dependências
./mvnw dependency:go-offline
./mvnw spring-boot:run
```

A aplicação sobe por padrão na porta `8080` (mesma porta exposta no `Dockerfile`, sem `server.port` customizado em `application.properties`). Com o serviço e o MongoDB no ar, `POST /internal/service-tokens` com o `clientSecret` configurado é um bom smoke test — não depende do `api-auth`, só do `SERVICE_CLIENT_SECRET` local, e confirma que o serviço subiu e consegue mintar um token M2M.

### Testando os endpoints

<p>
  <a href="https://github.com/syvixor/skills-icons">
    <img src="https://skills.syvixor.com/api/icons?i=swagger" height="48" alt="Documentação da API">
  </a>
</p>

O repositório expõe a documentação OpenAPI/Swagger via `springdoc-openapi-starter-webmvc-ui` (`OpenApiConfig`), disponível publicamente (sem autenticação) em `/swagger-ui.html` com o serviço no ar. As rotas `/messaging/**` exigem um JWT de usuário válido emitido pelo `api-auth` no header `Authorization: Bearer {token}`; as rotas `/internal/**` exigem um token de serviço obtido em `POST /internal/service-tokens`. {a confirmar: não há coleção Bruno/Postman versionada neste repositório — testes manuais de API dependem do Swagger UI ou de um cliente HTTP configurado à mão}.
