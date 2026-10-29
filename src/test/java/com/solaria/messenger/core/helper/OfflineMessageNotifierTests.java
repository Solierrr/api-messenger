package com.solaria.messenger.core.helper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.timeout;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;

import com.solaria.messenger.core.integration.notify.NotifyClient;
import com.solaria.messenger.core.model.Conversation;
import com.solaria.messenger.core.model.ConversationUserState;
import com.solaria.messenger.core.model.Message;
import com.solaria.messenger.core.model.enums.ConversationType;
import com.solaria.messenger.core.model.enums.MessageType;
import com.solaria.messenger.core.port.UserPresence;
import com.solaria.messenger.core.repository.ConversationRepository;
import com.solaria.messenger.core.repository.ConversationUserStateRepository;

@ExtendWith(MockitoExtension.class)
class OfflineMessageNotifierTests {

    private static final String CONVERSATION_ID = "conv-1";
    private static final String MESSAGE_ID = "msg-1";
    private static final UUID SENDER = UUID.randomUUID();
    private static final UUID OFFLINE = UUID.randomUUID();
    private static final UUID ONLINE = UUID.randomUUID();
    private static final UUID MUTED = UUID.randomUUID();
    private static final UUID ARCHIVED = UUID.randomUUID();

    @Mock
    private NotifyClient notify;
    @Mock
    private ConversationRepository conversations;
    @Mock
    private ConversationAccessHelper authorization;
    @Mock
    private ConversationUserStateRepository states;
    @Mock
    private UserPresence presence;

    private OfflineMessageNotifier notifier;

    @BeforeEach
    void setUp() {
        notifier = new OfflineMessageNotifier(notify, conversations, authorization, states, presence);
        lenient().when(notify.isEnabled()).thenReturn(true);
    }

    private Conversation conversation(ConversationType type, String title, UUID... participants) {
        Conversation conversation = new Conversation();
        conversation.setId(CONVERSATION_ID);
        conversation.setConversationType(type);
        conversation.setTitle(title);
        conversation.setParticipantIds(new LinkedHashSet<>(List.of(participants)));
        given(conversations.findById(CONVERSATION_ID)).willReturn(Optional.of(conversation));
        lenient().when(authorization.authorizedParticipants(any(List.class)))
                .thenReturn(Map.of(CONVERSATION_ID, Set.copyOf(conversation.getParticipantIds())));
        return conversation;
    }

    private Message message(MessageType type, String content) {
        Message message = new Message();
        message.setId(MESSAGE_ID);
        message.setConversationId(CONVERSATION_ID);
        message.setSenderId(SENDER);
        message.setMessageType(type);
        message.setContent(content);
        return message;
    }

    private ConversationUserState state(UUID user, boolean muted, boolean archived) {
        ConversationUserState state = new ConversationUserState();
        state.setConversationId(CONVERSATION_ID);
        state.setUserId(user);
        state.setMuted(muted);
        state.setArchived(archived);
        return state;
    }

    @Test
    void notifiesOnlyTheOfflineParticipantsOfADirectConversation() {
        conversation(ConversationType.DIRECT, null, SENDER, OFFLINE);
        given(presence.isOnline(OFFLINE)).willReturn(false);
        given(states.findByUsersAndConversations(any(), any())).willReturn(List.of());

        notifier.send(message(MessageType.USER_TO_USER, "Bom dia"));

        ArgumentCaptor<Map<String, String>> data = ArgumentCaptor.forClass(Map.class);
        verify(notify).dispatch(eq(OFFLINE), eq("NEW_MESSAGE"), eq("Nova mensagem"), eq("Bom dia"), data.capture());
        assertThat(data.getValue()).containsOnly(Map.entry("conversationId", CONVERSATION_ID),
                Map.entry("messageId", MESSAGE_ID));
        verify(notify, never()).dispatch(eq(SENDER), anyString(), anyString(), anyString(), any());
        verify(presence, never()).isOnline(SENDER);
    }

    @Test
    void skipsOnlineMutedAndArchivedParticipantsOfAGroup() {
        conversation(ConversationType.GROUP, "Projeto Sol", SENDER, OFFLINE, ONLINE, MUTED, ARCHIVED);
        given(presence.isOnline(ONLINE)).willReturn(true);
        given(presence.isOnline(OFFLINE)).willReturn(false);
        given(presence.isOnline(MUTED)).willReturn(false);
        given(presence.isOnline(ARCHIVED)).willReturn(false);
        given(states.findByUsersAndConversations(any(), any())).willReturn(List.of(
                state(OFFLINE, false, false), state(MUTED, true, false), state(ARCHIVED, false, true)));

        notifier.send(message(MessageType.USER_TO_GROUP, "Reunião amanhã"));

        verify(notify).dispatch(eq(OFFLINE), eq("NEW_MESSAGE"), eq("Nova mensagem em Projeto Sol"),
                eq("Reunião amanhã"), any());
        verify(notify, never()).dispatch(eq(ONLINE), anyString(), anyString(), anyString(), any());
        verify(notify, never()).dispatch(eq(MUTED), anyString(), anyString(), anyString(), any());
        verify(notify, never()).dispatch(eq(ARCHIVED), anyString(), anyString(), anyString(), any());
    }

    @Test
    void treatsAnUnavailablePresenceAsOffline() {
        conversation(ConversationType.DIRECT, null, SENDER, OFFLINE);
        given(presence.isOnline(OFFLINE)).willThrow(new IllegalStateException("redis"));
        given(states.findByUsersAndConversations(any(), any())).willReturn(List.of());

        notifier.send(message(MessageType.USER_TO_USER, "oi"));

        verify(notify).dispatch(eq(OFFLINE), anyString(), anyString(), anyString(), any());
    }

    @Test
    void doesNotNotifyChatbotConversations() {
        conversation(ConversationType.CHAT_BOT, null, SENDER);

        notifier.send(message(MessageType.USER_TO_CHATBOT, "oi"));

        verify(notify, never()).dispatch(any(), anyString(), anyString(), anyString(), any());
        verifyNoInteractions(presence, states);
    }

    @Test
    void doesNotQueueChatbotMessagesNorUnidentifiedSenders() {
        notifier.notifyOffline(message(MessageType.USER_TO_CHATBOT, "oi"));
        notifier.notifyOffline(message(MessageType.CHATBOT_TO_USER, "oi"));
        Message anonymous = message(MessageType.USER_TO_USER, "oi");
        anonymous.setSenderId(null);
        notifier.notifyOffline(anonymous);

        verifyNoInteractions(conversations, authorization, presence, states);
    }

    @Test
    void doesNothingWhenNotifyIsNotConfigured() {
        given(notify.isEnabled()).willReturn(false);

        notifier.notifyOffline(message(MessageType.USER_TO_USER, "oi"));

        verifyNoInteractions(conversations, authorization, presence, states);
    }

    @Test
    void runsOutsideTheCallerAndReachesTheNotify() {
        conversation(ConversationType.DIRECT, null, SENDER, OFFLINE);
        given(presence.isOnline(OFFLINE)).willReturn(false);
        given(states.findByUsersAndConversations(any(), any())).willReturn(List.of());

        notifier.notifyOffline(message(MessageType.USER_TO_USER, "oi"));

        verify(notify, timeout(2000)).dispatch(eq(OFFLINE), eq("NEW_MESSAGE"), anyString(), eq("oi"), any());
        notifier.shutdown();
    }

    @Test
    void truncatesLongMessagesToAOneLinePreview() {
        conversation(ConversationType.DIRECT, null, SENDER, OFFLINE);
        given(presence.isOnline(OFFLINE)).willReturn(false);
        given(states.findByUsersAndConversations(any(), any())).willReturn(List.of());
        String longText = ("linha um\nlinha dois " + "x".repeat(400));

        notifier.send(message(MessageType.USER_TO_USER, longText));

        ArgumentCaptor<String> body = ArgumentCaptor.forClass(String.class);
        verify(notify).dispatch(eq(OFFLINE), anyString(), anyString(), body.capture(), any());
        assertThat(body.getValue()).hasSize(100).startsWith("linha um linha dois x").endsWith("...")
                .doesNotContain("\n");
    }

    @Test
    void usesAGenericBodyWhenThereIsNoText() {
        conversation(ConversationType.DIRECT, null, SENDER, OFFLINE);
        given(presence.isOnline(OFFLINE)).willReturn(false);
        given(states.findByUsersAndConversations(any(), any())).willReturn(List.of());

        notifier.send(message(MessageType.USER_TO_USER, "   "));

        verify(notify).dispatch(eq(OFFLINE), anyString(), anyString(), eq("Você recebeu uma nova mensagem."), any());
    }

    @Test
    void keepsTheGroupTitleWithinTheNotifyLimit() {
        conversation(ConversationType.GROUP, "g".repeat(300), SENDER, OFFLINE);
        given(presence.isOnline(OFFLINE)).willReturn(false);
        given(states.findByUsersAndConversations(any(), any())).willReturn(List.of());

        notifier.send(message(MessageType.USER_TO_GROUP, "oi"));

        ArgumentCaptor<String> title = ArgumentCaptor.forClass(String.class);
        verify(notify).dispatch(eq(OFFLINE), anyString(), title.capture(), anyString(), any());
        assertThat(title.getValue()).hasSize(120).startsWith("Nova mensagem em g").endsWith("...");
    }

    @Test
    void aFailingRecipientDoesNotStopTheOthersNorPropagates() {
        UUID other = UUID.randomUUID();
        conversation(ConversationType.GROUP, "Grupo", SENDER, OFFLINE, other);
        given(presence.isOnline(any())).willReturn(false);
        given(states.findByUsersAndConversations(any(), any())).willReturn(List.of());
        doThrow(new RestClientException("falhou")).when(notify)
                .dispatch(eq(OFFLINE), anyString(), anyString(), anyString(), any());

        assertThatCode(() -> notifier.send(message(MessageType.USER_TO_GROUP, "oi"))).doesNotThrowAnyException();

        verify(notify).dispatch(eq(other), anyString(), anyString(), anyString(), any());
    }

    @Test
    void anUnreachableNotifyAbortsTheBatchWithoutPropagating() {
        UUID other = UUID.randomUUID();
        conversation(ConversationType.GROUP, "Grupo", SENDER, OFFLINE, other);
        given(presence.isOnline(any())).willReturn(false);
        given(states.findByUsersAndConversations(any(), any())).willReturn(List.of());
        doThrow(new ResourceAccessException("timeout")).when(notify)
                .dispatch(any(), anyString(), anyString(), anyString(), any());

        assertThatCode(() -> notifier.send(message(MessageType.USER_TO_GROUP, "oi"))).doesNotThrowAnyException();

        verify(notify, org.mockito.Mockito.times(1)).dispatch(any(), anyString(), anyString(), anyString(), any());
    }

    @Test
    void aRepositoryFailureDoesNotPropagate() {
        given(conversations.findById(CONVERSATION_ID)).willThrow(new IllegalStateException("mongo"));

        assertThatCode(() -> notifier.send(message(MessageType.USER_TO_USER, "oi"))).doesNotThrowAnyException();

        verify(notify, never()).dispatch(any(), anyString(), anyString(), anyString(), any());
    }
}
