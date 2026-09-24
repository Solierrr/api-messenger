package com.solaria.messenger;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling  // agendamento de tarefas em segundo plano | planejamento de polling para websocket
public class MessagerApplication {

	public static void main(String[] args) {
		SpringApplication.run(MessagerApplication.class, args);
	}

}
