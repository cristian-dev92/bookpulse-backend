package com.bookpulse.bookpulse_api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Clase principal de arranque de la API de BookPulse.
 * <p>
 * Activa los componentes esenciales del framework, incluyendo el mapeo de entidades
 * y el motor de tareas programadas en segundo plano.
 * </p>
 *
 * @author Cristian
 */
@SpringBootApplication
@EnableScheduling
public class BookpulseApiApplication {

	public static void main(String[] args) {
		SpringApplication.run(BookpulseApiApplication.class, args);
	}

}
