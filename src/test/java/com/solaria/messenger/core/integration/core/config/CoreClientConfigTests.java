package com.solaria.messenger.core.integration.core.config;

import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.headerDoesNotExist;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withNoContent;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

class CoreClientConfigTests {

    // mutate() preserva o header padrão e troca só a fábrica pelo mock (evita socket real)
    private MockRestServiceServer mock(String serviceKey, RestClient[] out) {
        var properties = new CoreClientProperties("http://core", Duration.ofSeconds(2), Duration.ofSeconds(2),
                serviceKey);
        RestClient.Builder builder = new CoreClientConfig().persistenceRestClient(properties).mutate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        out[0] = builder.build();
        return server;
    }

    @Test
    void sendsTheServiceKeyWhenConfigured() {
        RestClient[] client = new RestClient[1];
        MockRestServiceServer server = mock("chave-de-teste", client);
        server.expect(header("X-Service-Key", "chave-de-teste")).andRespond(withNoContent());

        client[0].get().uri("/x").retrieve().toBodilessEntity();

        server.verify();
    }

    @Test
    void omitsTheHeaderWhenNoKeyIsConfigured() {
        RestClient[] client = new RestClient[1];
        MockRestServiceServer server = mock("", client);
        server.expect(headerDoesNotExist("X-Service-Key")).andRespond(withNoContent());

        client[0].get().uri("/x").retrieve().toBodilessEntity();

        server.verify();
    }
}
