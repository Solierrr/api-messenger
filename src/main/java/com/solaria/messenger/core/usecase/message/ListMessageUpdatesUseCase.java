package com.solaria.messenger.core.usecase.message;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.springframework.stereotype.Service;

import com.solaria.messenger.core.contract.response.MessageResponseDTO;
import com.solaria.messenger.core.contract.response.MessageUpdatesResponseDTO;
import com.solaria.messenger.core.exception.InvalidFieldException;
import com.solaria.messenger.core.helper.ConversationAccessHelper;
import com.solaria.messenger.core.mapper.MessageMapper;
import com.solaria.messenger.core.model.Message;
import com.solaria.messenger.core.repository.ConversationUserStateRepository;
import com.solaria.messenger.core.repository.MessageRepository;
import com.solaria.messenger.core.security.rbac.RbacAuthorizationService;
import com.solaria.messenger.core.support.ConversationMutationGuard;
import com.solaria.messenger.core.support.ConversationSyncClock;

import static com.solaria.messenger.core.helper.PureRulesHelper.clampPageSize;

/**
 * Caso de uso do catch-up: devolve o que mudou nas mensagens do user desde a última versão que ele viu.
 *
 * <p>
 * Sem {@code since}, devolve só o ponto de partida. O cursor é a versão atual do relógio e a lista de mensagens vem
 * vazia. Com {@code since}, devolve o estado atual das mensagens com {@code changeVersion} maior que ele, em ordem
 * crescente. As conversas consideradas são as que o user lê hoje, pela mesma regra de
 * {@code /messaging/conversations/me}, e as que ele perdeu depois do {@code since}. Nas conversas perdidas, só entram
 * as mensagens até a versão da saída. A lista de conversas não faz parte da resposta. A página vai de 1 a 500
 * mensagens, com 100 por padrão. A consulta não altera nada.
 * </p>
 *
 * <p>
 * <b>Depende de:</b>
 * </p>
 * <ul>
 *     <li>{@link MessageRepository} -> busca as mensagens por versão.</li>
 *     <li>{@link ConversationUserStateRepository} -> lê as conversas que o user perdeu.</li>
 *     <li>{@link RbacAuthorizationService} -> identifica o user atual.</li>
 *     <li>{@link ConversationAccessHelper} -> devolve as conversas que o user lê hoje.</li>
 *     <li>{@link ConversationSyncClock} -> fornece a época e a versão atual.</li>
 *     <li>{@link ConversationMutationGuard} -> serializa a leitura de participação na JVM.</li>
 * </ul>
 *
 * <p>
 * Principal operação disponibilizada:
 * </p>
 * <ul>
 *     <li>{@link #execute(Long, Integer)} -> consulta a página de mudanças.</li>
 * </ul>
 */
@Service
public class ListMessageUpdatesUseCase {

    private final MessageRepository messageRepository;
    private final ConversationUserStateRepository userStates;
    private final RbacAuthorizationService rbac;
    private final ConversationAccessHelper access;
    private final ConversationSyncClock clock;
    private final ConversationMutationGuard guard;

    public ListMessageUpdatesUseCase(MessageRepository messages, ConversationUserStateRepository userStates,
            RbacAuthorizationService rbac, ConversationAccessHelper access, ConversationSyncClock clock,
            ConversationMutationGuard guard) {
        this.messageRepository = messages;
        this.userStates = userStates;
        this.rbac = rbac;
        this.access = access;
        this.clock = clock;
        this.guard = guard;
    }

    /** Conversas lidas por inteiro e conversas perdidas com a versão da saída. */
    private record Scope(Set<String> readable, Map<String, Long> leftAt) { }

    /**
     * Consulta as mudanças de mensagens do user depois da versão informada.
     *
     * @param since versão a partir da qual buscar (exclusiva). Sem valor, devolve só o ponto de partida.
     * @param limit tamanho da página. Sem valor, usa 100. Valores fora de 1 a 500 são ajustados para o limite.
     * @return época, cursor, indicação de mais páginas e as mensagens em ordem crescente de {@code changeVersion}.
     * @throws InvalidFieldException caso {@code since} seja negativo.
     */
    public MessageUpdatesResponseDTO execute(Long since, Integer limit) {
        // toda versão já alocada é menor ou igual à atual, então o próximo since só pega o que vier depois
        if (since == null) {
            long cursor = clock.currentVersion();
            return new MessageUpdatesResponseDTO(clock.epoch(), cursor, false, List.of());
        }
        if (since < 0) throw new InvalidFieldException("since deve ser maior ou igual a zero.");

        UUID actor = rbac.currentUserId();
        int pageSize = clampPageSize(limit);
        Scope scope = guard.execute(() -> scopeOf(actor, since));

        // uma mensagem a mais do que a página mostra diz se ainda há o que buscar
        List<Message> found = messageRepository.findUpdates(scope.readable(), scope.leftAt(), since, pageSize + 1);
        List<MessageResponseDTO> page = found.stream().limit(pageSize).map(MessageMapper::toResponse).toList();
        long cursor = page.isEmpty() ? since : page.getLast().getChangeVersion();
        return new MessageUpdatesResponseDTO(clock.epoch(), cursor, found.size() > pageSize, page);
    }

    /** Lê as duas listas dentro do guard, para uma saída em curso nesta JVM não aparecer pela metade */
    private Scope scopeOf(UUID actor, long since) {
        Set<String> readable = access.readableConversationIds(actor);
        Map<String, Long> leftAt = new LinkedHashMap<>();
        userStates.findLeftAfter(actor, since).stream()
                .filter(state -> !readable.contains(state.getConversationId()))
                .forEach(state -> leftAt.put(state.getConversationId(), state.getLeftAtVersion()));
        return new Scope(readable, leftAt);
    }
}
