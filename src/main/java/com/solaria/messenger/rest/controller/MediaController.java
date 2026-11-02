package com.solaria.messenger.rest.controller;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.solaria.messenger.core.contract.request.MediaUploadRequestDTO;
import com.solaria.messenger.core.contract.response.MediaListResponseDTO;
import com.solaria.messenger.core.contract.response.MediaUploadResponseDTO;
import com.solaria.messenger.core.usecase.media.CreateMediaUploadUseCase;
import com.solaria.messenger.core.usecase.media.GetMediaContentUseCase;
import com.solaria.messenger.core.usecase.media.ListAvailableMediaUseCase;
import com.solaria.messenger.rest.openapi.MediaOpenApi;

import jakarta.validation.Valid;

/**
 * Controller da transferência de mídia.
 *
 * <p>Atua na camada de entrada HTTP e delega aos casos de uso. Os bytes nunca passam por aqui: o aparelho envia e baixa
 * direto do armazenamento com as permissões que esta classe devolve.</p>
 * <ul>
 *     <li>{@link #createUpload(MediaUploadRequestDTO)} -> pede a permissão de envio (201).</li>
 *     <li>{@link #getContent(String)} -> redireciona (302) para o link temporário de retirada.</li>
 *     <li>{@link #listMedia(String, Integer, String)} -> lista as mídias ainda disponíveis.</li>
 * </ul>
 */
@RestController
@RequestMapping("/messaging/media")
public class MediaController implements MediaOpenApi {

    private final CreateMediaUploadUseCase createUpload;
    private final GetMediaContentUseCase getContent;
    private final ListAvailableMediaUseCase listMedia;

    public MediaController(CreateMediaUploadUseCase createUpload, GetMediaContentUseCase getContent,
            ListAvailableMediaUseCase listMedia) {
        this.createUpload = createUpload;
        this.getContent = getContent;
        this.listMedia = listMedia;
    }

    @Override
    @PostMapping
    public ResponseEntity<MediaUploadResponseDTO> createUpload(@Valid @RequestBody MediaUploadRequestDTO dto) {
        return ResponseEntity.status(HttpStatus.CREATED).body(createUpload.execute(dto));
    }

    /** O link assinado vai cru no Location, para não ser codificado de novo; e nunca fica em cache. */
    @Override
    @GetMapping("/{mediaId}/content")
    public ResponseEntity<Void> getContent(@PathVariable String mediaId) {
        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.LOCATION, getContent.execute(mediaId))
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .build();
    }

    @Override
    @GetMapping
    public ResponseEntity<MediaListResponseDTO> listMedia(@RequestParam(required = false) String conversationId,
            @RequestParam(required = false) Integer limit, @RequestParam(required = false) String cursor) {
        return ResponseEntity.ok(listMedia.execute(conversationId, limit, cursor));
    }
}
