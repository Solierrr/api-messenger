package com.solaria.messenger.core.usecase.message;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import com.solaria.messenger.core.contract.response.MessageResponseDTO;
import com.solaria.messenger.core.helper.MessageMutationResult;
import com.solaria.messenger.core.helper.MessageWriteHelper;
import com.solaria.messenger.core.helper.NotificationHelper;
import com.solaria.messenger.core.model.Attachment;
import com.solaria.messenger.core.model.Message;
import com.solaria.messenger.core.model.enums.AttachmentType;
import com.solaria.messenger.core.port.MediaStore;
import com.solaria.messenger.core.repository.MessageRepository;

/**
 * Cobre a exclusão com anexo: o arquivo sai do armazenamento junto com a mensagem, depois do aviso, e uma falha do
 * armazenamento nunca desfaz nem atrapalha a exclusão (o prazo do bucket é a rede de segurança).
 */
class DeleteMessageUseCaseTests {

    private MessageWriteHelper writes;
    private NotificationHelper notifications;
    private MediaStore store;
    private MessageRepository messages;
    private DeleteMessageUseCase useCase;
    private Message deleted;

    @BeforeEach
    void setUp() {
        writes = mock(MessageWriteHelper.class);
        notifications = mock(NotificationHelper.class);
        store = mock(MediaStore.class);
        messages = mock(MessageRepository.class);
        useCase = new DeleteMessageUseCase(writes, notifications, store, messages);
        deleted = new Message();
        deleted.setId("msg-1");
        deleted.setDeleted(true);
    }

    @Test
    void deletingAMessageWithAFileRemovesTheFileAfterNotifying() {
        given(writes.mutate("msg-1", null, true)).willReturn(new MessageMutationResult(deleted, true, file("m1")));

        MessageResponseDTO response = useCase.execute("msg-1");

        assertThat(response.isDeleted()).isTrue();
        InOrder order = inOrder(notifications, store);
        order.verify(notifications).notifyMessageDeleted(deleted);
        order.verify(store).delete("m1");
    }

    @Test
    void theFileStaysWhileAnotherLiveMessageStillCitesIt() {
        // um reenvio da fila do app pode gravar duas mensagens com o mesmo arquivo
        given(writes.mutate("msg-1", null, true)).willReturn(new MessageMutationResult(deleted, true, file("m1")));
        given(messages.existsByAttachmentMediaIdAndDeletedFalse("m1")).willReturn(true);

        useCase.execute("msg-1");

        verify(notifications).notifyMessageDeleted(deleted);
        verify(store, never()).delete(any());
    }

    @Test
    void aFailureRemovingTheFileDoesNotBreakTheDeletion() {
        given(writes.mutate("msg-1", null, true)).willReturn(new MessageMutationResult(deleted, true, file("m1")));
        doThrow(new IllegalStateException("gcs fora")).when(store).delete("m1");

        assertThatCode(() -> useCase.execute("msg-1")).doesNotThrowAnyException();
        verify(notifications).notifyMessageDeleted(deleted);
    }

    @Test
    void aLocationOrAPlainTextMessageHasNoFileToRemove() {
        Attachment location = new Attachment();
        location.setType(AttachmentType.LOCATION);
        given(writes.mutate("msg-1", null, true)).willReturn(new MessageMutationResult(deleted, true, location));
        useCase.execute("msg-1");
        given(writes.mutate("msg-2", null, true)).willReturn(new MessageMutationResult(deleted, true));
        useCase.execute("msg-2");

        verify(store, never()).delete(any());
    }

    @Test
    void deletingAnAlreadyDeletedMessageTouchesNothing() {
        given(writes.mutate("msg-1", null, true)).willReturn(new MessageMutationResult(deleted, false));

        useCase.execute("msg-1");

        verify(notifications, never()).notifyMessageDeleted(any());
        verify(store, never()).delete(any());
    }

    private static Attachment file(String mediaId) {
        Attachment attachment = new Attachment();
        attachment.setType(AttachmentType.IMAGE);
        attachment.setMediaId(mediaId);
        return attachment;
    }
}
