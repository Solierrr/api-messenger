package com.solaria.messenger.core.helper;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;

import com.solaria.messenger.core.exception.BusinessRuleException;
import com.solaria.messenger.core.exception.MessageMutationNotAppliedException;
import com.solaria.messenger.core.exception.MessageSendFailedException;
import com.solaria.messenger.core.model.Conversation;
import com.solaria.messenger.core.model.Message;
import com.solaria.messenger.core.model.enums.ConversationType;
import com.solaria.messenger.core.repository.ConversationRepository;
import com.solaria.messenger.core.repository.MessageRepository;
import com.solaria.messenger.core.security.rbac.RbacAuthorizationService;
import com.solaria.messenger.core.support.ConversationMutationGuard;
import com.solaria.messenger.core.support.ConversationSyncClock;

/**
 * Cobre o carimbo de {@code changeVersion}: o envio leva a versão do relógio no insert, e a edição e a exclusão a levam
 * no mesmo CAS. Sem gravação (CAS que não aplica, mensagem já no estado pedido), nenhuma versão é consumida pelo
 * resultado devolvido.
 */
@ExtendWith(MockitoExtension.class)
class MessageWriteHelperTests {

    private static final UUID AUTHOR = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final String CONVERSATION = "conv-1";

    @Mock
    private MessageRepository messages;
    @Mock
    private ConversationRepository conversations;
    @Mock
    private RbacAuthorizationService rbac;
    @Mock
    private ConversationAccessHelper access;
    @Mock
    private ConversationSyncClock clock;

    private MessageWriteHelper helper;
    private Conversation conversation;

    @BeforeEach
    void setUp() {
        helper = new MessageWriteHelper(messages, conversations, rbac, access, new ConversationMutationGuard(), clock,
                Duration.ofMinutes(30));
        conversation = new Conversation();
        conversation.setId(CONVERSATION);
        conversation.setConversationType(ConversationType.GROUP);
    }

    @Test
    void sendStampsTheClockVersionOnTheInsertedMessage() {
        given(clock.nextVersion()).willReturn(77L);
        given(messages.insertWithPersistedSequence(any(Message.class))).willAnswer(call -> call.getArgument(0));

        Message saved = helper.appendAndTouch(conversation, newMessage());

        ArgumentCaptor<Message> inserted = ArgumentCaptor.forClass(Message.class);
        verify(messages).insertWithPersistedSequence(inserted.capture());
        assertThat(inserted.getValue().getChangeVersion()).isEqualTo(77L);
        assertThat(saved.getChangeVersion()).isEqualTo(77L);
    }

    @Test
    void sendFailsWithoutInsertingWhenTheClockIsUnavailable() {
        given(clock.nextVersion()).willThrow(new IllegalStateException("mongo fora"));

        assertThatThrownBy(() -> helper.appendAndTouch(conversation, newMessage()))
                .isInstanceOfSatisfying(MessageSendFailedException.class, failure -> {
                    assertThat(failure.getStatus()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
                    assertThat(failure.getErrorCode()).isEqualTo("DEPENDENCY_UNAVAILABLE");
                });
        verifyNoInteractions(messages);
    }

    @Test
    void editStampsTheClockVersionInTheSameCas() {
        Message current = storedMessage();
        givenAuthorizedMutation(current);
        given(clock.nextVersion()).willReturn(90L);
        given(messages.editIfCurrent("msg-1", CONVERSATION, AUTHOR, 3L, "novo", 4L, 90L)).willReturn(1L);

        MessageMutationResult result = helper.mutate("msg-1", "novo", false);

        assertThat(result.changed()).isTrue();
        assertThat(result.message().getChangeVersion()).isEqualTo(90L);
        assertThat(result.message().getMutationEpoch()).isEqualTo(4L);
        assertThat(result.message().getContent()).isEqualTo("novo");
    }

    @Test
    void deleteStampsTheClockVersionInTheSameCas() {
        Message current = storedMessage();
        givenAuthorizedMutation(current);
        given(clock.nextVersion()).willReturn(91L);
        given(messages.deleteIfCurrent("msg-1", CONVERSATION, AUTHOR, 3L, 4L, 91L)).willReturn(1L);

        MessageMutationResult result = helper.mutate("msg-1", null, true);

        assertThat(result.changed()).isTrue();
        assertThat(result.message().isDeleted()).isTrue();
        assertThat(result.message().getContent()).isNull();
        assertThat(result.message().getChangeVersion()).isEqualTo(91L);
    }

    @Test
    void repeatedDeleteWritesNothingAndKeepsTheStoredVersion() {
        Message current = storedMessage();
        current.setDeleted(true);
        current.setChangeVersion(55L);
        givenAuthorizedMutation(current);

        MessageMutationResult result = helper.mutate("msg-1", null, true);

        assertThat(result.changed()).isFalse();
        assertThat(result.message().getChangeVersion()).isEqualTo(55L);
        verify(clock, never()).nextVersion();
        verify(messages, never()).deleteIfCurrent(any(), any(), any(), anyLong(), anyLong(), anyLong());
    }

    @Test
    void editThatLosesTheCasFailsAndDoesNotChangeTheMessage() {
        Message current = storedMessage();
        givenAuthorizedMutation(current);
        given(clock.nextVersion()).willReturn(92L);
        given(messages.editIfCurrent(eq("msg-1"), eq(CONVERSATION), eq(AUTHOR), eq(3L), eq("novo"), eq(4L), eq(92L)))
                .willReturn(0L);

        assertThatThrownBy(() -> helper.mutate("msg-1", "novo", false))
                .isInstanceOf(MessageMutationNotAppliedException.class);
        assertThat(current.getChangeVersion()).isEqualTo(10L);
    }

    @Test
    void editAndDeleteInsideTheWindowAreWritten() {
        Message current = storedMessage();
        current.setTimestamp(Instant.now().minus(Duration.ofMinutes(29)));
        givenAuthorizedMutation(current);
        given(clock.nextVersion()).willReturn(93L, 94L);
        given(messages.editIfCurrent("msg-1", CONVERSATION, AUTHOR, 3L, "novo", 4L, 93L)).willReturn(1L);

        assertThat(helper.mutate("msg-1", "novo", false).changed()).isTrue();
    }

    @Test
    void editOutsideTheWindowIsRefusedWithoutWriting() {
        Message current = storedMessage();
        current.setTimestamp(Instant.now().minus(Duration.ofMinutes(31)));
        givenAuthorizedMutation(current);

        assertThatThrownBy(() -> helper.mutate("msg-1", "novo", false)).isInstanceOf(BusinessRuleException.class);
        verify(messages, never()).editIfCurrent(any(), any(), any(), anyLong(), any(), anyLong(), anyLong());
        verify(clock, never()).nextVersion();
    }

    @Test
    void deleteOutsideTheWindowIsRefusedWithoutWriting() {
        Message current = storedMessage();
        current.setTimestamp(Instant.now().minus(Duration.ofMinutes(31)));
        givenAuthorizedMutation(current);

        assertThatThrownBy(() -> helper.mutate("msg-1", null, true)).isInstanceOf(BusinessRuleException.class);
        verify(messages, never()).deleteIfCurrent(any(), any(), any(), anyLong(), anyLong(), anyLong());
    }

    @Test
    void repeatedDeleteOutsideTheWindowStillSucceedsWithoutWriting() {
        Message current = storedMessage();
        current.setTimestamp(Instant.now().minus(Duration.ofDays(2)));
        current.setDeleted(true);
        givenAuthorizedMutation(current);

        assertThat(helper.mutate("msg-1", null, true).changed()).isFalse();
        verify(messages, never()).deleteIfCurrent(any(), any(), any(), anyLong(), anyLong(), anyLong());
    }

    @Test
    void messageWithoutTimestampNeverExpires() {
        Message current = storedMessage();
        current.setTimestamp(null);
        givenAuthorizedMutation(current);
        given(clock.nextVersion()).willReturn(95L);
        given(messages.deleteIfCurrent("msg-1", CONVERSATION, AUTHOR, 3L, 4L, 95L)).willReturn(1L);

        assertThat(helper.mutate("msg-1", null, true).changed()).isTrue();
    }

    private void givenAuthorizedMutation(Message current) {
        given(rbac.currentUserId()).willReturn(AUTHOR);
        given(messages.findById("msg-1")).willReturn(java.util.Optional.of(current));
        given(access.requireParticipant(CONVERSATION, AUTHOR)).willReturn(conversation);
    }

    private static Message newMessage() {
        Message message = new Message();
        message.setConversationId(CONVERSATION);
        message.setSenderId(AUTHOR);
        message.setContent("oi");
        message.setTimestamp(Instant.parse("2026-10-28T10:00:00Z"));
        return message;
    }

    private static Message storedMessage() {
        Message message = newMessage();
        message.setId("msg-1");
        message.setTimestamp(Instant.now().minus(Duration.ofMinutes(5)));
        message.setMutationEpoch(3L);
        message.setChangeVersion(10L);
        return message;
    }
}
