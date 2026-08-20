package com.bookpulse.bookpulse_api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Clase principal de arranque de la API de BookPulse.
 * <p>
 * Activa los componentes esenciales del framework, incluyendo el mapeo de entidades,
 * el motor de tareas programadas en segundo plano ({@link EnableScheduling}), la
 * ejecución asíncrona ({@link EnableAsync}) usada por el envío de emails transaccionales
 * para no bloquear las respuestas HTTP, y el soporte de caché ({@link EnableCaching})
 * empleado por el catálogo de servicios públicos.
 * </p>
 *
 * @author Cristian
 */
@SpringBootApplication
@EnableScheduling
@EnableAsync
@EnableCaching
public class BookpulseApiApplication {

	public static void main(String[] args) {
		SpringApplication.run(BookpulseApiApplication.class, args);
	}

}
