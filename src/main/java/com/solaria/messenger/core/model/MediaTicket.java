package com.solaria.messenger.core.model;

import java.time.Instant;
import java.util.Map;

/**
 * Permissão temporária para falar direto com o armazenamento: enviar ou retirar um arquivo.
 *
 * @param url endereço assinado.
 * @param method método HTTP a usar na {@code url}.
 * @param headers cabeçalhos que o cliente precisa mandar exatamente como vieram, porque entram na assinatura.
 * @param expiresAt até quando a permissão vale.
 */
public record MediaTicket(String url, String method, Map<String, String> headers, Instant expiresAt) {
    public MediaTicket {
        headers = headers == null ? Map.of() : Map.copyOf(headers);
    }
}
