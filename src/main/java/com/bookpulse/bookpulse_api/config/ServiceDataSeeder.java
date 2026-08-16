package com.bookpulse.bookpulse_api.config;

import com.bookpulse.bookpulse_api.model.Service;
import com.bookpulse.bookpulse_api.repository.ServiceRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.List;

/**
 * Inicializador que siembra el catálogo de servicios por defecto
 * la primera vez que la aplicación arranca con la tabla vacía.
 * <p>
 * Garantiza que los IDs 1, 2 y 3 existan al iniciar la base de datos
 * para que el frontend pueda cargar el catálogo de forma dinámica.
 * </p>
 *
 * @author Cristian
 */
@Component
public class ServiceDataSeeder implements CommandLineRunner {

    private final ServiceRepository serviceRepository;

    public ServiceDataSeeder(ServiceRepository serviceRepository) {
        this.serviceRepository = serviceRepository;
    }

    @Override
    public void run(String... args) {
        if (serviceRepository.count() > 0) {
            return;
        }

        List<Service> defaultServices = List.of(
                Service.builder()
                        .name("Corte / Consulta Básica")
                        .description("Corte de cabello o consulta estándar realizada por nuestro equipo profesional.")
                        .durationMinutes(30)
                        .price(new BigDecimal("25.00"))
                        .build(),
                Service.builder()
                        .name("Servicio Completo / Mantenimiento")
                        .description("Servicio completo de mantenimiento y estilismo personalizado.")
                        .durationMinutes(60)
                        .price(new BigDecimal("45.00"))
                        .build(),
                Service.builder()
                        .name("Tratamiento Premium")
                        .description("Tratamiento premium integral con productos exclusivos.")
                        .durationMinutes(90)
                        .price(new BigDecimal("70.00"))
                        .build()
        );

        serviceRepository.saveAll(defaultServices);
        System.out.println("✅ Se han creado " + defaultServices.size() + " servicios por defecto.");
    }
}