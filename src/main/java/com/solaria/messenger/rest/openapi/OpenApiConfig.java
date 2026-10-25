package com.solaria.messenger.rest.openapi;

import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Info;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;


/**
 * Configuração da documentação OpenAPI (Swagger) do serviço.
 *
 * <p>Declara o bean {@link OpenAPI} com título, descrição e versão exibidos no Swagger.</p>
 *
 * <p>Principais beans disponibilizados:</p>
 * <ul>
 *     <li>{@link #solierOpenApi()} - metadados do documento OpenAPI.</li>
 * </ul>
 */
@Configuration
public class OpenApiConfig {

    /** Metadados do documento OpenAPI exibido no Swagger. */
    @Bean
    public OpenAPI solierOpenApi() {
        return new OpenAPI()
                .info(new Info()
                        .title("Solier/api-messenger")
                        .description("API Spring de acesso ao Mongo Db do Solier.")
                        .version("v1"));
    }
}
