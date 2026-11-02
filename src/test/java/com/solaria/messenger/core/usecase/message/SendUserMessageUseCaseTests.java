package com.solaria.messenger.core.usecase.message;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.util.UUID;
import java.util.function.Function;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import com.solaria.messenger.core.contract.request.AttachmentRequestDTO;
import com.solaria.messenger.core.contract.request.MessageRequestDTO;
import com.solaria.messenger.core.contract.response.MessageResponseDTO;
import com.solaria.messenger.core.exception.BusinessRuleException;
import com.solaria.messenger.core.exception.InvalidFieldException;
import com.solaria.messenger.core.helper.AttachmentHelper;
import com.solaria.messenger.core.helper.ConversationAccessHelper;
import com.solaria.messenger.core.helper.MessageWriteHelper;
import com.solaria.messenger.core.helper.NotificationHelper;
import com.solaria.messenger.core.helper.OfflineMessageNotifier;
import com.solaria.messenger.core.model.Attachment;
import com.solaria.messenger.core.model.Conversation;
import com.solaria.messenger.core.model.Message;
import com.solaria.messenger.core.model.enums.AttachmentType;
import com.solaria.messenger.core.model.enums.ConversationType;
import com.solaria.messenger.core.model.enums.MessageType;
import com.solaria.messenger.core.security.rbac.RbacAuthorizationService;
import com.solaria.messenger.core.support.ConversationMutationGuard;

/**
 * Cobre a regra "texto ou anexo" do envio e a gravação do anexo na mensagem. A validação do próprio anexo é do
 * {@link AttachmentHelper}.
 */
class SendUserMessageUseCaseTests {

    private static final UUID ME = UUID.fromString("11111111-1111-1111-1111-111111111111");

    private AttachmentHelper attachments;
    private MessageWriteHelper writes;
    private SendUserMessageUseCase useCase;
    private Conversation conversation;
    private final ConversationMutationGuard guard = new ConversationMutationGuard();

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        RbacAuthorizationService rbac = mock(RbacAuthorizationService.class);
        given(rbac.currentUserId()).willReturn(ME);
        ConversationAccessHelper access = mock(ConversationAccessHelper.class);
        conversation = new Conversation();
        conversation.setId("conv-1");
        conversation.setConversationType(ConversationType.DIRECT);
        given(access.withAccess(eq("conv-1"), eq(ME), any(Function.class)))
                .willAnswer(call -> ((Function<Conversation, Object>) call.getArgument(2)).apply(conversation));
        writes = mock(MessageWriteHelper.class);
        given(writes.appendAndTouch(any(Conversation.class), any(Message.class))).willAnswer(call -> call.getArgument(1));
        attachments = mock(AttachmentHelper.class);
        useCase = new SendUserMessageUseCase(rbac, access, writes, attachments, mock(NotificationHelper.class),
                mock(OfflineMessageNotifier.class), guard);
    }

    @Test
    void messageWithOnlyAnAttachmentIsSavedWithEmptyContent() {
        Attachment location = new Attachment();
        location.setType(AttachmentType.LOCATION);
        AttachmentRequestDTO request = new AttachmentRequestDTO();
        request.setType(AttachmentType.LOCATION);
        given(attachments.build("conv-1", ME, request)).willReturn(location);

        MessageResponseDTO response = useCase.execute(dto(null, request));

        ArgumentCaptor<Message> saved = ArgumentCaptor.forClass(Message.class);
        verify(writes).appendAndTouch(any(Conversation.class), saved.capture());
        assertThat(saved.getValue().getContent()).isEmpty();
        assertThat(saved.getValue().getAttachment()).isSameAs(location);
        assertThat(response.getAttachment().type()).isEqualTo(AttachmentType.LOCATION);
    }

    @Test
    void captionAndAttachmentTravelTogether() {
        Attachment location = new Attachment();
        location.setType(AttachmentType.LOCATION);
        AttachmentRequestDTO request = new AttachmentRequestDTO();
        given(attachments.build("conv-1", ME, request)).willReturn(location);

        useCase.execute(dto("estou aqui", request));

        ArgumentCaptor<Message> saved = ArgumentCaptor.forClass(Message.class);
        verify(writes).appendAndTouch(any(Conversation.class), saved.capture());
        assertThat(saved.getValue().getContent()).isEqualTo("estou aqui");
    }

    @Test
    void messageWithoutTextAndWithoutAttachmentIsRefused() {
        assertThatThrownBy(() -> useCase.execute(dto(null, null))).isInstanceOf(InvalidFieldException.class);
        assertThatThrownBy(() -> useCase.execute(dto("   ", null))).isInstanceOf(InvalidFieldException.class);
        verify(writes, never()).appendAndTouch(any(), any());
    }

    @Test
    void theAttachmentIsCheckedBeforeEnteringTheGuardBecauseItAsksTheStorage() {
        AttachmentRequestDTO request = new AttachmentRequestDTO();
        Attachment file = new Attachment();
        file.setType(AttachmentType.IMAGE);
        boolean[] insideGuard = { true };
        given(attachments.build("conv-1", ME, request)).willAnswer(call -> {
            insideGuard[0] = guard.isHeldByCurrentThread();
            return file;
        });

        useCase.execute(dto(null, request));

        assertThat(insideGuard[0]).isFalse();
    }

    @Test
    void conversationWithTheAssistantRefusesAnAttachmentWithoutSaving() {
        conversation.setConversationType(ConversationType.CHAT_BOT);
        AttachmentRequestDTO request = new AttachmentRequestDTO();
        given(attachments.build("conv-1", ME, request)).willReturn(new Attachment());
        MessageRequestDTO dto = dto("oi", request);
        dto.setMessageType(MessageType.USER_TO_CHATBOT);

        assertThatThrownBy(() -> useCase.execute(dto)).isInstanceOf(BusinessRuleException.class);
        verify(writes, never()).appendAndTouch(any(), any());
    }

    @Test
    void plainTextKeepsWorkingWithoutTouchingTheAttachmentHelper() {
        useCase.execute(dto("oi", null));

        verifyNoInteractions(attachments);
        ArgumentCaptor<Message> saved = ArgumentCaptor.forClass(Message.class);
        verify(writes).appendAndTouch(any(Conversation.class), saved.capture());
        assertThat(saved.getValue().getContent()).isEqualTo("oi");
        assertThat(saved.getValue().getAttachment()).isNull();
    }

    private static MessageRequestDTO dto(String content, AttachmentRequestDTO attachment) {
        MessageRequestDTO dto = new MessageRequestDTO();
        dto.setConversationId("conv-1");
        dto.setMessageType(MessageType.USER_TO_USER);
        dto.setContent(content);
        dto.setAttachment(attachment);
        return dto;
    }
}
