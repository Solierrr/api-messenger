package com.solaria.messenger.core.integration.notify;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.headerDoesNotExist;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.jsonPath;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.method;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withServerError;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;

import java.time.Duration;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import com.solaria.messenger.core.integration.notify.config.NotifyClientConfig;
import com.solaria.messenger.core.integration.notify.config.NotifyClientProperties;

class NotifyClientTests {

    private static final UUID RECIPIENT = UUID.fromString("8d6f2a4b-1c3e-4f5a-9b7c-2d4e6f8a0b1c");

    private NotifyClientProperties properties(String baseUrl, String serviceKey) {
        return new NotifyClientProperties(baseUrl, Duration.ofSeconds(2), Duration.ofSeconds(3), serviceKey);
    }

    private MockRestServiceServer mock(NotifyClientProperties properties, NotifyClient[] out) {
        RestClient.Builder builder = new NotifyClientConfig().notifyRestClient(properties).mutate();
        MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();
        out[0] = new NotifyClient(builder.build(), properties);
        return server;
    }

    @Test
    void postsTheNotificationWithTheServiceKey() {
        NotifyClient[] client = new NotifyClient[1];
        MockRestServiceServer server = mock(properties("http://notify", "chave-de-teste"), client);
        server.expect(requestTo("http://notify/internal/notifications"))
                .andExpect(method(HttpMethod.POST))
                .andExpect(header("X-Service-Key", "chave-de-teste"))
                .andExpect(jsonPath("$.authId").value(RECIPIENT.toString()))
                .andExpect(jsonPath("$.type").value("NEW_MESSAGE"))
                .andExpect(jsonPath("$.title").value("Nova mensagem"))
                .andExpect(jsonPath("$.body").value("oi"))
                .andExpect(jsonPath("$.data.conversationId").value("conv-1"))
                .andExpect(jsonPath("$.data.messageId").value("msg-1"))
                .andRespond(withStatus(HttpStatus.CREATED));

        client[0].dispatch(RECIPIENT, "NEW_MESSAGE", "Nova mensagem", "oi",
                Map.of("conversationId", "conv-1", "messageId", "msg-1"));

        server.verify();
    }

    @Test
    void omitsTheHeaderWhenNoKeyIsConfigured() {
        NotifyClient[] client = new NotifyClient[1];
        MockRestServiceServer server = mock(properties("http://notify", ""), client);
        server.expect(headerDoesNotExist("X-Service-Key")).andRespond(withStatus(HttpStatus.CREATED));

        client[0].dispatch(RECIPIENT, "NEW_MESSAGE", "t", "b", Map.of());

        server.verify();
    }

    @Test
    void failureStatusSurfacesAsRestClientException() {
        NotifyClient[] client = new NotifyClient[1];
        MockRestServiceServer server = mock(properties("http://notify", "chave-de-teste"), client);
        server.expect(requestTo("http://notify/internal/notifications")).andRespond(withServerError());

        assertThatThrownBy(() -> client[0].dispatch(RECIPIENT, "NEW_MESSAGE", "t", "b", Map.of()))
                .isInstanceOf(RestClientException.class);
    }

    @Test
    void isDisabledWithoutBaseUrl() {
        NotifyClient[] client = new NotifyClient[1];
        mock(properties("", "chave-de-teste"), client);
        assertThat(client[0].isEnabled()).isFalse();

        mock(properties(null, "chave-de-teste"), client);
        assertThat(client[0].isEnabled()).isFalse();
    }

    @Test
    void isEnabledWithBaseUrl() {
        NotifyClient[] client = new NotifyClient[1];
        mock(properties("http://notify", ""), client);
        assertThat(client[0].isEnabled()).isTrue();
    }
}
