package com.solaria.messenger.rest.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.solaria.messenger.core.contract.response.MessageUpdatesResponseDTO;
import com.solaria.messenger.core.usecase.message.ListMessageUpdatesUseCase;
import com.solaria.messenger.rest.openapi.UpdatesOpenApi;

/**
 * Controller REST do catch-up de mensagens, exposto em {@code /messaging/updates}.
 *
 * <p>
 * Atua na camada de entrada HTTP: recebe os parâmetros e delega a consulta ao {@link ListMessageUpdatesUseCase}. O
 * contrato da API é declarado em {@link UpdatesOpenApi}.
 * </p>
 *
 * <p>
 * Principal operação disponibilizada:
 * </p>
 * <ul>
 *     <li>{@link #getUpdates(Long, Integer)} -> devolve as mensagens que mudaram depois de uma versão.</li>
 * </ul>
 */
@RestController
@RequestMapping("/messaging/updates")
public class UpdatesController implements UpdatesOpenApi {

    private final ListMessageUpdatesUseCase listUpdates;

    public UpdatesController(ListMessageUpdatesUseCase listUpdates) {
        this.listUpdates = listUpdates;
    }

    /** Devolve a página de mudanças depois de {@code since}, ou só o ponto de partida quando ele não vem. */
    @Override
    @GetMapping
    public ResponseEntity<MessageUpdatesResponseDTO> getUpdates(@RequestParam(required = false) Long since,
            @RequestParam(required = false) Integer limit) {
        return ResponseEntity.ok(listUpdates.execute(since, limit));
    }
}
