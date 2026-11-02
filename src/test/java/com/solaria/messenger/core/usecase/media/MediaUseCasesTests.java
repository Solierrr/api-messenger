package com.solaria.messenger.core.usecase.media;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.util.unit.DataSize;

import com.solaria.messenger.core.contract.request.MediaUploadRequestDTO;
import com.solaria.messenger.core.contract.response.MediaListResponseDTO;
import com.solaria.messenger.core.contract.response.MediaUploadResponseDTO;
import com.solaria.messenger.core.exception.BusinessRuleException;
import com.solaria.messenger.core.exception.DependencyUnavailableException;
import com.solaria.messenger.core.exception.InvalidFieldException;
import com.solaria.messenger.core.exception.MediaExpiredException;
import com.solaria.messenger.core.exception.UnauthorizedAccessException;
import com.solaria.messenger.core.helper.ConversationAccessHelper;
import com.solaria.messenger.core.helper.MediaRulesHelper;
import com.solaria.messenger.core.integration.storage.MediaProperties;
import com.solaria.messenger.core.model.Attachment;
import com.solaria.messenger.core.model.Conversation;
import com.solaria.messenger.core.model.MediaTicket;
import com.solaria.messenger.core.model.Message;
import com.solaria.messenger.core.model.StoredMedia;
import com.solaria.messenger.core.model.enums.AttachmentType;
import com.solaria.messenger.core.model.enums.ConversationType;
import com.solaria.messenger.core.port.MediaStore;
import com.solaria.messenger.core.repository.MessageRepository;
import com.solaria.messenger.core.security.rbac.RbacAuthorizationService;

/** Cobre depositar, retirar e listar mídia: quem pode, o que é recusado, o prazo e a falha do armazenamento. */
class MediaUseCasesTests {

    private static final UUID ME = UUID.fromString("11111111-1111-1111-1111-111111111111");
    private static final MediaProperties PROPERTIES = new MediaProperties("bucket", Duration.ofDays(7),
            Duration.ofMinutes(15), Duration.ofMinutes(10), DataSize.ofMegabytes(10), DataSize.ofMegabytes(16),
            DataSize.ofMegabytes(64), DataSize.ofMegabytes(25));

    private RbacAuthorizationService rbac;
    private ConversationAccessHelper access;
    private MediaStore store;
    private Conversation conversation;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        rbac = mock(RbacAuthorizationService.class);
        given(rbac.currentUserId()).willReturn(ME);
        access = mock(ConversationAccessHelper.class);
        store = mock(MediaStore.class);
        conversation = new Conversation();
        conversation.setId("conv-1");
        conversation.setConversationType(ConversationType.GROUP);
        given(access.withAccess(eq("conv-1"), eq(ME), any(Function.class)))
                .willAnswer(call -> ((Function<Conversation, Object>) call.getArgument(2)).apply(conversation));
    }

    @Nested
    class Upload {
        private CreateMediaUploadUseCase useCase;

        @BeforeEach
        void build() {
            useCase = new CreateMediaUploadUseCase(rbac, access, new MediaRulesHelper(PROPERTIES), store);
        }

        @Test
        void participantGetsAMediaIdAndASendPermission() {
            Instant until = Instant.parse("2026-11-02T12:15:00Z");
            given(store.createUpload(any(), eq("conv-1"), eq(ME), eq("image/jpeg"), eq(2048L)))
                    .willReturn(new MediaTicket("https://bucket/put", "PUT", Map.of("Content-Type", "image/jpeg"), until));

            MediaUploadResponseDTO response = useCase.execute(request(AttachmentType.IMAGE, "image/jpeg", 2048));

            assertThat(response.mediaId()).isNotBlank();
            assertThat(response.upload().url()).isEqualTo("https://bucket/put");
            assertThat(response.upload().method()).isEqualTo("PUT");
            assertThat(response.upload().headers()).containsEntry("Content-Type", "image/jpeg");
            assertThat(response.uploadExpiresAt()).isEqualTo(until);
            assertThat(response.maxBytes()).isEqualTo(10L * 1024 * 1024);
            ArgumentCaptor<String> mediaId = ArgumentCaptor.forClass(String.class);
            verify(store).createUpload(mediaId.capture(), eq("conv-1"), eq(ME), eq("image/jpeg"), eq(2048L));
            assertThat(mediaId.getValue()).isEqualTo(response.mediaId());
        }

        @Test
        void eachRequestGetsANewMediaId() {
            given(store.createUpload(any(), any(), any(), any(), anyLong()))
                    .willReturn(new MediaTicket("u", "PUT", Map.of(), Instant.now()));

            String first = useCase.execute(request(AttachmentType.FILE, "application/pdf", 10)).mediaId();
            String second = useCase.execute(request(AttachmentType.FILE, "application/pdf", 10)).mediaId();

            assertThat(first).isNotEqualTo(second);
        }

        @Test
        void someoneWhoIsNotAParticipantIsRefusedBeforeTouchingTheStore() {
            given(access.withAccess(eq("conv-1"), eq(ME), any(Function.class))).willThrow(new UnauthorizedAccessException("não participa"));

            assertThatThrownBy(() -> useCase.execute(request(AttachmentType.IMAGE, "image/jpeg", 10)))
                    .isInstanceOf(UnauthorizedAccessException.class);
            verifyNoInteractions(store);
        }

        @Test
        void archivedCommunityIsReadOnlyAndRefusesNewMedia() {
            doThrow(new BusinessRuleException("comunidade arquivada")).when(access).requireCommunityActive(conversation);

            assertThatThrownBy(() -> useCase.execute(request(AttachmentType.IMAGE, "image/jpeg", 10)))
                    .isInstanceOf(BusinessRuleException.class);
            verifyNoInteractions(store);
        }

        @Test
        void conversationWithTheAssistantDoesNotTakeAttachments() {
            conversation.setConversationType(ConversationType.CHAT_BOT);

            assertThatThrownBy(() -> useCase.execute(request(AttachmentType.IMAGE, "image/jpeg", 10)))
                    .isInstanceOf(BusinessRuleException.class);
            verifyNoInteractions(store);
        }

        @Test
        void aMimeOrSizeOutsideTheRulesIsRefusedBeforeTouchingTheStore() {
            assertThatThrownBy(() -> useCase.execute(request(AttachmentType.IMAGE, "video/mp4", 10)))
                    .isInstanceOf(InvalidFieldException.class);
            assertThatThrownBy(() -> useCase.execute(request(AttachmentType.IMAGE, "image/jpeg", 11L * 1024 * 1024)))
                    .isInstanceOf(InvalidFieldException.class);
            verifyNoInteractions(store);
        }

        @Test
        void storageFailureBecomesDependencyUnavailable() {
            given(store.createUpload(any(), any(), any(), any(), anyLong())).willThrow(new IllegalStateException("gcs fora"));

            assertThatThrownBy(() -> useCase.execute(request(AttachmentType.IMAGE, "image/jpeg", 10)))
                    .isInstanceOf(DependencyUnavailableException.class);
        }

        private MediaUploadRequestDTO request(AttachmentType type, String mime, long size) {
            MediaUploadRequestDTO request = new MediaUploadRequestDTO();
            request.setConversationId("conv-1");
            request.setType(type);
            request.setMimeType(mime);
            request.setSizeBytes(size);
            return request;
        }
    }

    @Nested
    class Content {
        private GetMediaContentUseCase useCase;

        @BeforeEach
        void build() {
            useCase = new GetMediaContentUseCase(rbac, access, store, PROPERTIES);
        }

        @Test
        void participantGetsAFreshDownloadLink() {
            given(store.stat("m1")).willReturn(Optional.of(stored(Instant.now().minus(Duration.ofDays(1)))));
            given(store.createDownload("m1")).willReturn(new MediaTicket("https://bucket/get", "GET", Map.of(), Instant.now()));

            assertThat(useCase.execute("m1")).isEqualTo("https://bucket/get");
            verify(access).requireParticipant("conv-1", ME);
        }

        @Test
        void aMissingFileIsGone() {
            given(store.stat("m1")).willReturn(Optional.empty());

            assertThatThrownBy(() -> useCase.execute("m1")).isInstanceOf(MediaExpiredException.class);
        }

        @Test
        void afterSevenDaysTheFileIsGoneEvenIfTheBucketHasNotCleanedItYet() {
            given(store.stat("m1")).willReturn(Optional.of(stored(Instant.now().minus(Duration.ofDays(7)).minusSeconds(5))));

            assertThatThrownBy(() -> useCase.execute("m1")).isInstanceOf(MediaExpiredException.class);
            verify(store, never()).createDownload(any());
        }

        @Test
        void someoneWhoLeftTheConversationCannotDownloadAnymore() {
            given(store.stat("m1")).willReturn(Optional.of(stored(Instant.now())));
            given(access.requireParticipant("conv-1", ME)).willThrow(new UnauthorizedAccessException("saiu"));

            assertThatThrownBy(() -> useCase.execute("m1")).isInstanceOf(UnauthorizedAccessException.class);
            verify(store, never()).createDownload(any());
        }

        @Test
        void storageFailureBecomesDependencyUnavailable() {
            given(store.stat("m1")).willThrow(new IllegalStateException("gcs fora"));

            assertThatThrownBy(() -> useCase.execute("m1")).isInstanceOf(DependencyUnavailableException.class);
        }

        private StoredMedia stored(Instant createdAt) {
            return new StoredMedia("conv-1", ME, "image/jpeg", 100, createdAt);
        }
    }

    @Nested
    class Listing {
        private final MessageRepository messages = mock(MessageRepository.class);
        private ListAvailableMediaUseCase useCase;

        @BeforeEach
        void build() {
            useCase = new ListAvailableMediaUseCase(rbac, access, messages);
        }

        @Test
        void listsLiveMediaOfEveryConversationTheUserCanRead() {
            given(access.readableConversationIds(ME)).willReturn(Set.of("conv-1", "conv-2"));
            given(messages.findAvailableMedia(eq(Set.of("conv-1", "conv-2")), any(Instant.class), eq(Long.MAX_VALUE), eq(51)))
                    .willReturn(List.of(withMedia("m-a", "conv-1", 30), withMedia("m-b", "conv-2", 20)));

            MediaListResponseDTO response = useCase.execute(null, null, null);

            assertThat(response.items()).hasSize(2);
            assertThat(response.items().get(0).messageId()).isEqualTo("m-a");
            assertThat(response.items().get(0).attachment().downloadPath()).isEqualTo("/messaging/media/media-m-a/content");
            assertThat(response.nextCursor()).isNull();
        }

        @Test
        void aFullPageReturnsACursorForTheNextOne() {
            given(access.readableConversationIds(ME)).willReturn(Set.of("conv-1"));
            given(messages.findAvailableMedia(any(), any(Instant.class), eq(Long.MAX_VALUE), eq(3)))
                    .willReturn(List.of(withMedia("m1", "conv-1", 30), withMedia("m2", "conv-1", 20), withMedia("m3", "conv-1", 10)));

            MediaListResponseDTO response = useCase.execute(null, 2, null);

            assertThat(response.items()).extracting("messageId").containsExactly("m1", "m2");
            assertThat(response.nextCursor()).isEqualTo("20");
        }

        @Test
        void theCursorContinuesFromTheLastVersionSeen() {
            given(access.readableConversationIds(ME)).willReturn(Set.of("conv-1"));
            given(messages.findAvailableMedia(any(), any(Instant.class), eq(20L), anyInt())).willReturn(List.of());

            assertThat(useCase.execute(null, null, "20").items()).isEmpty();
        }

        @Test
        void oneConversationRequiresParticipation() {
            given(messages.findAvailableMedia(eq(Set.of("conv-1")), any(Instant.class), anyLong(), anyInt())).willReturn(List.of());

            useCase.execute("conv-1", null, null);

            verify(access).requireParticipant("conv-1", ME);
            verify(access, never()).readableConversationIds(any());
        }

        @Test
        void someoneWhoIsNotAParticipantCannotListAConversation() {
            given(access.requireParticipant("conv-1", ME)).willThrow(new UnauthorizedAccessException("não participa"));

            assertThatThrownBy(() -> useCase.execute("conv-1", null, null)).isInstanceOf(UnauthorizedAccessException.class);
            verifyNoInteractions(messages);
        }

        @Test
        void withoutReadableConversationsNothingIsQueried() {
            given(access.readableConversationIds(ME)).willReturn(Set.of());

            assertThat(useCase.execute(null, null, null).items()).isEmpty();
            verifyNoInteractions(messages);
        }

        @Test
        void aMalformedCursorIsRefusedAndTheLimitIsClamped() {
            assertThatThrownBy(() -> useCase.execute(null, null, "abc")).isInstanceOf(InvalidFieldException.class);
            given(access.readableConversationIds(ME)).willReturn(Set.of("conv-1"));
            given(messages.findAvailableMedia(any(), any(Instant.class), anyLong(), eq(101))).willReturn(List.of());

            useCase.execute(null, 500, null);

            verify(messages).findAvailableMedia(any(), any(Instant.class), anyLong(), eq(101));
        }

        private Message withMedia(String id, String conversationId, long version) {
            Attachment attachment = new Attachment();
            attachment.setType(AttachmentType.IMAGE);
            attachment.setMediaId("media-" + id);
            attachment.setExpiresAt(Instant.now().plus(Duration.ofDays(3)));
            Message message = new Message();
            message.setId(id);
            message.setConversationId(conversationId);
            message.setSenderId(ME);
            message.setChangeVersion(version);
            message.setTimestamp(Instant.now());
            message.setAttachment(attachment);
            return message;
        }
    }
}
