package com.solaria.messenger;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Ponto de entrada do serviço api-messenger (Spring Boot).
 *
 * <p>Inicializa o contexto da aplicação e habilita o agendamento de tarefas, usado apenas para a
 * expiração periódica do estado de digitação local.</p>
 */
@SpringBootApplication
@EnableScheduling  // mantém somente a expiração periódica do typing local
public class MessagerApplication {

	/** Ponto de entrada da aplicação Spring Boot. */
	public static void main(String[] args) {
		SpringApplication.run(MessagerApplication.class, args);
	}

}
