package com.solaria.messenger.core.contract.request;

import java.util.Map;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;


/**
 * DTO de requisição para criação de uma conversa com o chatbot.
 *
 */
@Getter
@Setter
public class ChatbotConversationRequestDTO {

    @NotBlank(message = "userType é obrigatório")
    private String userType;

    private Map<String, Object> userDetails;

    /**
     * Id da conversa escolhido por quem chama (opcional).
     *
     * <p>
     * O sandbox de IA usa o Session ID dele como id da conversa, para a busca por Session ID achar a conversa. Sem ele,
     * o id é gerado. Se já existir uma conversa do chatbot com esse id e do mesmo usuário, ela é reaproveitada.
     * </p>
     */
    @Pattern(regexp = "^[A-Za-z0-9][A-Za-z0-9_-]{7,63}$",
            message = "conversationId deve ter de 8 a 64 caracteres (letras, números, _ e -)")
    private String conversationId;
}
