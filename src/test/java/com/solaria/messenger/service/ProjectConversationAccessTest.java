package com.solaria.messenger.service;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

import com.solaria.messenger.exception.UnauthorizedAccessException;
import com.solaria.messenger.integration.ProjectAuthorityClient;
import com.solaria.messenger.model.*;
import com.solaria.messenger.repository.*;
import com.solaria.messenger.security.rbac.RbacAuthorizationService;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.*;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.mongodb.core.MongoTemplate;

import java.util.*;

@ExtendWith(MockitoExtension.class)
class ProjectConversationAccessTest {
    @Mock ConversationRepository conversations;
    @Mock CommunityRepository communities;
    @Mock RbacAuthorizationService rbac;
    @Mock MongoTemplate mongo;
    @Mock ProjectAuthorityClient authority;
    @InjectMocks ConversationService service;

    @Test
    void staleMongoParticipantsDoNotKeepAccessAfterRevocation() {
        var user = UUID.randomUUID();
        var project = UUID.randomUUID();
        var c = new Conversation();
        c.setCommunityId("private");
        c.setParticipantIds(Set.of(user));
        var group = new ProjectCommunity();
        group.setProjectId(project);
        when(communities.findById("private")).thenReturn(Optional.of(group));
        when(authority.access(project, user)).thenThrow(new UnauthorizedAccessException("Removed"));
        assertFalse(service.canReadFor(c, user));
    }

    @Test
    void newProjectMemberCanReadGeneralConversationWithoutStaleMongoRoster() {
        var user = UUID.randomUUID();
        var project = UUID.randomUUID();
        var c = new Conversation();
        c.setCommunityId("private");
        c.setProjectGeneral(true);
        c.setParticipantIds(Set.of());
        var group = new ProjectCommunity();
        group.setProjectId(project);
        when(communities.findById("private")).thenReturn(Optional.of(group));
        when(authority.access(project, user))
                .thenReturn(
                        new ProjectAuthorityClient.Access(
                                "MEMBER", false, Map.of(user, "MEMBER"), "Project"));
        assertTrue(service.canReadFor(c, user));
    }

    @Test
    void subgroupStillRequiresExplicitParticipation() {
        var user = UUID.randomUUID();
        var project = UUID.randomUUID();
        var c = new Conversation();
        c.setCommunityId("private");
        c.setParticipantIds(Set.of(UUID.randomUUID()));
        var group = new ProjectCommunity();
        group.setProjectId(project);
        when(communities.findById("private")).thenReturn(Optional.of(group));
        when(authority.access(project, user))
                .thenReturn(
                        new ProjectAuthorityClient.Access(
                                "MEMBER", false, Map.of(user, "MEMBER"), "Project"));
        assertFalse(service.canReadFor(c, user));
    }
}
