package com.solaria.messenger.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.client.match.MockRestRequestMatchers.*;
import static org.springframework.test.web.client.response.MockRestResponseCreators.*;

import com.solaria.messenger.exception.*;
import com.solaria.messenger.integration.ProjectAuthorityClient;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.client.MockRestServiceServer;
import org.springframework.web.client.RestClient;

import java.util.UUID;

class ProjectAuthorityContractTest {
    @Test
    void canonicalUuidRosterAndPrivateHeaderAreParsed() {
        var builder = RestClient.builder().baseUrl("http://core.test");
        var server = MockRestServiceServer.bindTo(builder).build();
        var user = UUID.randomUUID();
        var project = UUID.randomUUID();
        String key = "test-key".repeat(4);
        server.expect(
                        requestTo(
                                "http://core.test/internal/projects/"
                                        + project
                                        + "/access/"
                                        + user))
                .andExpect(header("X-Service-Key", key))
                .andRespond(
                        withSuccess(
                                "{\"role\":\"MEMBER\",\"readOnly\":false,\"members\":{\""
                                        + user
                                        + "\":\"MEMBER\"},\"name\":\"Private project\"}",
                                MediaType.APPLICATION_JSON));
        var authority = new ProjectAuthorityClient(builder.build(), key);
        assertEquals("MEMBER", authority.access(project, user).members().get(user));
        server.verify();
    }

    @Test
    void removedMemberAndUnconfiguredAuthorityFailClosed() {
        var builder = RestClient.builder().baseUrl("http://core.test");
        var server = MockRestServiceServer.bindTo(builder).build();
        server.expect(anything()).andRespond(withStatus(HttpStatus.FORBIDDEN));
        assertThrows(
                UnauthorizedAccessException.class,
                () ->
                        new ProjectAuthorityClient(builder.build(), "test-key".repeat(4))
                                .access(UUID.randomUUID(), UUID.randomUUID()));
        assertThrows(
                DependencyUnavailableException.class,
                () ->
                        new ProjectAuthorityClient(builder.build(), "")
                                .access(UUID.randomUUID(), UUID.randomUUID()));
        server.verify();
    }

    @Test
    void unknownProjectRolesDoNotGrantConversationAccess() {
        var builder = RestClient.builder().baseUrl("http://core.test");
        var server = MockRestServiceServer.bindTo(builder).build();
        var user = UUID.randomUUID();
        server.expect(anything())
                .andRespond(
                        withSuccess(
                                "{\"role\":\"UNKNOWN\",\"readOnly\":false,\"members\":{\""
                                        + user
                                        + "\":\"UNKNOWN\"},\"name\":\"Project\"}",
                                MediaType.APPLICATION_JSON));
        assertThrows(
                UnauthorizedAccessException.class,
                () ->
                        new ProjectAuthorityClient(builder.build(), "test-key".repeat(4))
                                .access(UUID.randomUUID(), user));
        server.verify();
    }
}
