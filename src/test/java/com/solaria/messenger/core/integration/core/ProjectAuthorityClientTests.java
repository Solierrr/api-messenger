package com.solaria.messenger.core.integration.core;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.anything;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.header;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.requestTo;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withStatus;
import static org.springframework.test.web.client.response.MockRestResponseCreators.withSuccess;

import java.time.Duration;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import com.solaria.messenger.core.exception.DependencyUnavailableException;
import com.solaria.messenger.core.exception.UnauthorizedAccessException;
import com.solaria.messenger.core.integration.core.config.CoreClientProperties;

class ProjectAuthorityClientTests {

    private static final String KEY = "test-key".repeat(4);

    private final RestClient.Builder builder = RestClient.builder().baseUrl("http://core.test");
    private final MockRestServiceServer server = MockRestServiceServer.bindTo(builder).build();

    private ProjectAuthorityClient client(String key) {
        return new ProjectAuthorityClient(builder.build(),
                new CoreClientProperties("http://core.test", Duration.ofSeconds(1), Duration.ofSeconds(1), key));
    }

    private static String access(String role, boolean readOnly, UUID user) {
        return "{\"role\":\"" + role + "\",\"readOnly\":" + readOnly + ",\"members\":{\"" + user + "\":\"" + role
                + "\"},\"name\":\"Private project\"}";
    }

    @Test
    void sendsTheServiceKeyAndParsesTheRoster() {
        UUID user = UUID.randomUUID();
        UUID project = UUID.randomUUID();
        server.expect(requestTo("http://core.test/internal/projects/" + project + "/access/" + user))
                .andExpect(header("X-Service-Key", KEY))
                .andRespond(withSuccess(access("MEMBER", false, user), MediaType.APPLICATION_JSON));

        assertEquals("MEMBER", client(KEY).access(project, user).members().get(user));
        server.verify();
    }

    @Test
    void removedMemberAndUnconfiguredAuthorityFailClosed() {
        server.expect(anything()).andRespond(withStatus(HttpStatus.FORBIDDEN));

        assertThrows(UnauthorizedAccessException.class, () -> client(KEY).access(UUID.randomUUID(), UUID.randomUUID()));
        assertThrows(DependencyUnavailableException.class, () -> client("").access(UUID.randomUUID(), UUID.randomUUID()));
        assertThrows(DependencyUnavailableException.class, () -> client(null).access(UUID.randomUUID(), UUID.randomUUID()));
        server.verify();
    }

    @Test
    void unknownProjectRolesDoNotGrantAccess() {
        UUID user = UUID.randomUUID();
        server.expect(anything()).andRespond(withSuccess(access("UNKNOWN", false, user), MediaType.APPLICATION_JSON));

        assertThrows(UnauthorizedAccessException.class, () -> client(KEY).access(UUID.randomUUID(), user));
        server.verify();
    }

    @Test
    void viewersAndReadOnlyProjectsCannotWrite() {
        UUID user = UUID.randomUUID();
        server.expect(anything()).andRespond(withSuccess(access("VIEWER", false, user), MediaType.APPLICATION_JSON));
        server.expect(anything()).andRespond(withSuccess(access("MANAGER", true, user), MediaType.APPLICATION_JSON));

        ProjectAuthorityClient authority = client(KEY);
        assertThrows(UnauthorizedAccessException.class, () -> authority.requireWritable(UUID.randomUUID(), user));
        assertThrows(UnauthorizedAccessException.class, () -> authority.requireWritable(UUID.randomUUID(), user));
        server.verify();
    }

    @Test
    void serverErrorsBecomeDependencyUnavailable() {
        server.expect(anything()).andRespond(withStatus(HttpStatus.BAD_GATEWAY));

        assertThrows(DependencyUnavailableException.class,
                () -> client(KEY).access(UUID.randomUUID(), UUID.randomUUID()));
        server.verify();
    }
}
