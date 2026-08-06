package com.bookpulse.bookpulse_api.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Configuración global de seguridad para la API de BookPulse.
 * <p>
 * Define las reglas de acceso a los endpoints, la política de sesiones
 * y el codificador de contraseñas oficial del sistema.
 * </p>
 *
 * @author Cristian
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    /**
     * Define la cadena de filtros de seguridad.
     * <p>
     * De momento, permite el acceso total a todas las rutas para que podamos
     * probar el flujo de citas mientras implementamos el registro de usuarios.
     * </p>
     *
     * @param http Componente de configuración de seguridad inyectado por Spring.
     * @return La configuración del filtro construida.
     * @throws Exception Si ocurre un fallo en la configuración de los filtros.
     */
    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .csrf(AbstractHttpConfigurer::disable) // Deshabilitamos CSRF porque usaremos tokens JWT
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/api/v1/auth/**").permitAll()
                        .requestMatchers("/api/appointments/**").permitAll() // Tus endpoints de citas libres
                        .anyRequest().permitAll() // Permitimos el resto temporalmente para construir el login
                );

        return http.build();
    }

    // Definimos la fuente de configuración de CORS permitida
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(List.of("http://localhost:5173")); // El origen de tu React
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Requested-With"));
        configuration.setAllowCredentials(true);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }

    /**
     * Define el algoritmo de encriptación para las contraseñas de los usuarios.
     * <p>
     * Utiliza BCrypt, un algoritmo de hashing seguro que aplica "salado" (salts)
     * aleatorios de forma nativa para evitar ataques de tablas arcoíris.
     * </p>
     *
     * @return Una instancia de {@link BCryptPasswordEncoder}.
     */
    @Bean
    public PasswordEncoder passwordEncoder() {

        return new BCryptPasswordEncoder();
    }
}
