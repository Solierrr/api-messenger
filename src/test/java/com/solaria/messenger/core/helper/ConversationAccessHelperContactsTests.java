package com.solaria.messenger.core.helper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.solaria.messenger.core.model.Conversation;
import com.solaria.messenger.core.model.ProjectCommunity;
import com.solaria.messenger.core.repository.CommunityRepository;
import com.solaria.messenger.core.repository.ConversationRepository;

/**
 * Cobre {@link ConversationAccessHelper#contactsOf(UUID)}: contatos são quem divide com o user alguma conversa que ele
 * pode ler, sem o próprio user.
 */
class ConversationAccessHelperContactsTests {

    private static final UUID A = UUID.fromString("aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa");
    private static final UUID B = UUID.fromString("bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb");
    private static final UUID C = UUID.fromString("cccccccc-cccc-cccc-cccc-cccccccccccc");

    private ConversationRepository conversations;
    private CommunityRepository communities;
    private ConversationAccessHelper access;

    @BeforeEach
    void setUp() {
        conversations = mock(ConversationRepository.class);
        communities = mock(CommunityRepository.class);
        access = new ConversationAccessHelper(conversations, communities, mock(LookupHelper.class));
    }

    @Test
    void contactsAreTheUnionOfAuthorizedParticipantsWithoutTheActor() {
        given(conversations.findByParticipantIdsContainingOrderByLastInteractionAtDesc(A))
                .willReturn(List.of(conversation("direct", null, A, B), conversation("group", null, A, B, C)));

        assertThat(access.contactsOf(A)).containsExactlyInAnyOrder(B, C);
    }

    @Test
    void subgroupWhereTheActorLeftTheCommunityAddsNoContacts() {
        ProjectCommunity community = mock(ProjectCommunity.class);
        given(community.isMember(A)).willReturn(false);
        given(community.isMember(B)).willReturn(true);
        given(communities.findAllById(any())).willReturn(List.of(community));
        given(community.getId()).willReturn("community-1");
        given(conversations.findByParticipantIdsContainingOrderByLastInteractionAtDesc(A))
                .willReturn(List.of(conversation("sub", "community-1", A, B)));

        assertThat(access.contactsOf(A)).isEmpty();
    }

    @Test
    void actorWithoutConversationsHasNoContacts() {
        given(conversations.findByParticipantIdsContainingOrderByLastInteractionAtDesc(A)).willReturn(List.of());

        assertThat(access.contactsOf(A)).isEmpty();
    }

    private static Conversation conversation(String id, String communityId, UUID... participants) {
        Conversation conversation = new Conversation();
        conversation.setId(id);
        conversation.setCommunityId(communityId);
        conversation.setParticipantIds(new LinkedHashSet<>(Set.of(participants)));
        return conversation;
    }
}
