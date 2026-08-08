package com.bookpulse.bookpulse_api.config;

import com.bookpulse.bookpulse_api.model.Role;
import com.bookpulse.bookpulse_api.model.User;
import com.bookpulse.bookpulse_api.repository.UserRepository;
import org.springframework.boot.CommandLineRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

@Component
public class AdminDataLoader implements CommandLineRunner {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public AdminDataLoader(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    @Override
    public void run(String... args) throws Exception {
        // 1. Crear usuario DEMO ADMIN si no existe
        if (!userRepository.existsByEmail("admin@demo.com")) {
            User admin = new User();
            admin.setName("Administrador Demo");
            admin.setEmail("admin@demo.com");
            admin.setPassword(passwordEncoder.encode("admin123"));
            admin.setRole(Role.ROLE_ADMIN);

            userRepository.save(admin);
            System.out.println("✅ Cuenta Demo Admin creada (admin@demo.com)");
        }

        // 2. Crear usuario DEMO USER si no existe
        if (!userRepository.existsByEmail("user@demo.com")) {
            User user = new User();
            user.setName("Usuario Demo");
            user.setEmail("user@demo.com");
            user.setPassword(passwordEncoder.encode("user123"));
            user.setRole(Role.ROLE_CLIENT);

            userRepository.save(user);
            System.out.println("✅ Cuenta Demo User creada (user@demo.com)");
        }
    }
}