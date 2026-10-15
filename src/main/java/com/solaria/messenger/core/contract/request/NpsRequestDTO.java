package com.solaria.messenger.core.contract.request;

import com.solaria.messenger.core.model.enums.Environment;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

/**
 * DTO de requisição para registro de uma resposta de NPS (Net Promoter Score).
 *
 * <p>Usado no endpoint {@code POST /messaging/nps}. Carrega a conversa avaliada, a nota de 0 a 10 e
 * o ambiente de origem da resposta.</p>
 */
@Setter
@Getter
public class NpsRequestDTO {

    @NotBlank(message = "O identificador da conversa é obrigatório")
    private String conversationId;

    @NotNull(message = "A nota é obrigatória")
    @Min(value = 0, message = "A nota deve ser maior ou igual a 0")
    @Max(value = 10, message = "A nota deve ser menor ou igual a 10")
    private Integer score;

    private Environment environment;
}
