package com.solaria.messenger.dto.request;

import com.solaria.messenger.model.enums.Environment;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

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
