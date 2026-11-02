package com.solaria.messenger.rest.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.BDDMockito.given;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.solaria.messenger.core.contract.request.MediaUploadRequestDTO;
import com.solaria.messenger.core.contract.response.AttachmentResponseDTO;
import com.solaria.messenger.core.contract.response.MediaListItemResponseDTO;
import com.solaria.messenger.core.contract.response.MediaListResponseDTO;
import com.solaria.messenger.core.contract.response.MediaUploadResponseDTO;
import com.solaria.messenger.core.exception.MediaExpiredException;
import com.solaria.messenger.core.model.enums.AttachmentType;
import com.solaria.messenger.core.security.rbac.RbacAuthorizationService;
import com.solaria.messenger.core.usecase.media.CreateMediaUploadUseCase;
import com.solaria.messenger.core.usecase.media.GetMediaContentUseCase;
import com.solaria.messenger.core.usecase.media.ListAvailableMediaUseCase;
import com.solaria.messenger.rest.error.ProblemDetailFactory;

/** Cobre o contrato HTTP da mídia: depósito, retirada por redirecionamento, listagem e o 410 do prazo. */
@WebMvcTest(MediaController.class)
@Import(ProblemDetailFactory.class)
class MediaControllerTests {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CreateMediaUploadUseCase createUpload;

    @MockitoBean
    private GetMediaContentUseCase getContent;

    @MockitoBean
    private ListAvailableMediaUseCase listMedia;

    // dependências do StompErrorHandler, que é @ControllerAdvice e entra no slice do @WebMvcTest
    @MockitoBean
    private RbacAuthorizationService rbac;

    @MockitoBean
    private SimpMessagingTemplate messaging;

    @Test
    void createsAnUploadPermission() throws Exception {
        given(createUpload.execute(any(MediaUploadRequestDTO.class))).willReturn(new MediaUploadResponseDTO("m1",
                new MediaUploadResponseDTO.Target("https://bucket/put", "PUT", Map.of("Content-Type", "image/jpeg")),
                Instant.parse("2026-11-02T12:15:00Z"), 10485760L));

        mockMvc.perform(post("/messaging/media").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"conversationId\":\"conv-1\",\"type\":\"IMAGE\",\"mimeType\":\"image/jpeg\",\"sizeBytes\":2048}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.mediaId").value("m1"))
                .andExpect(jsonPath("$.upload.method").value("PUT"))
                .andExpect(jsonPath("$.upload.headers['Content-Type']").value("image/jpeg"))
                .andExpect(jsonPath("$.maxBytes").value(10485760));
    }

    @Test
    void rejectsAnUploadRequestWithoutTypeOrSize() throws Exception {
        mockMvc.perform(post("/messaging/media").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"conversationId\":\"conv-1\",\"mimeType\":\"image/jpeg\"}"))
                .andExpect(status().isBadRequest());
        mockMvc.perform(post("/messaging/media").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"conversationId\":\"conv-1\",\"type\":\"IMAGE\",\"mimeType\":\"image/jpeg\",\"sizeBytes\":0}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void contentRedirectsToTheTemporaryLinkWithoutCaching() throws Exception {
        given(getContent.execute("m1")).willReturn("https://bucket/get?sig=abc");

        mockMvc.perform(get("/messaging/media/m1/content"))
                .andExpect(status().isFound())
                .andExpect(header().string("Location", "https://bucket/get?sig=abc"))
                .andExpect(header().string("Cache-Control", "no-store"));
    }

    @Test
    void anExpiredFileAnswersGone() throws Exception {
        given(getContent.execute("m1")).willThrow(new MediaExpiredException());

        mockMvc.perform(get("/messaging/media/m1/content")).andExpect(status().isGone());
    }

    @Test
    void listsTheAvailableMediaWithTheNextCursor() throws Exception {
        AttachmentResponseDTO attachment = new AttachmentResponseDTO(AttachmentType.IMAGE, "m1", "image/jpeg", 2048L, null,
                null, 800, 600, null, Instant.parse("2026-11-09T10:00:00Z"), "/messaging/media/m1/content", null, null, null);
        given(listMedia.execute(isNull(), org.mockito.ArgumentMatchers.eq(10), isNull())).willReturn(new MediaListResponseDTO(
                List.of(new MediaListItemResponseDTO("msg-1", "conv-1", UUID.fromString("11111111-1111-1111-1111-111111111111"),
                        Instant.parse("2026-11-02T10:00:00Z"), attachment)), "30"));

        mockMvc.perform(get("/messaging/media").param("limit", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[0].messageId").value("msg-1"))
                .andExpect(jsonPath("$.items[0].attachment.downloadPath").value("/messaging/media/m1/content"))
                .andExpect(jsonPath("$.nextCursor").value("30"));
    }
}
