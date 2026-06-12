package com.bookpulse.bookpulse_api.service;

import com.bookpulse.bookpulse_api.dto.AuthResponse;
import com.bookpulse.bookpulse_api.dto.LoginRequest;
import com.bookpulse.bookpulse_api.dto.RegisterRequest;
import com.bookpulse.bookpulse_api.model.Role;
import com.bookpulse.bookpulse_api.model.User;
import com.bookpulse.bookpulse_api.repository.UserRepository;
import com.bookpulse.bookpulse_api.security.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

/**
 * Servicio encargado de la lógica de negocio de registro e inicio de sesión.
 * * @author Cristian
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;

    /**
     * Registra un nuevo cliente en el sistema encriptando su contraseña.
     */
    public AuthResponse register(RegisterRequest request) {
        // Verificamos si el email ya existe
        if (userRepository.findByEmail(request.getEmail()).isPresent()) {
            throw new RuntimeException("El correo electrónico ya está registrado");
        }

        User user = new User();
        user.setEmail(request.getEmail());
        // ¡Crucial! Encriptamos la contraseña con BCrypt antes de guardarla en Neon
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setName(request.getName());
        user.setRole(Role.ROLE_CLIENT); // Por defecto, el registro web es para clientes

        userRepository.save(user);

        String token = jwtService.generateToken(user);
        return AuthResponse.builder()
                .token(token)
                .email(user.getEmail())
                .name(user.getName())
                .role(user.getRole().name())
                .build();
    }

    /**
     * Autentica a un usuario y le devuelve su token JWT si las credenciales son válidas.
     */
    public AuthResponse login(LoginRequest request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        // Comparamos la contraseña en texto plano con el hash encriptado de la BD
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new RuntimeException("Contraseña incorrecta");
        }

        String token = jwtService.generateToken(user);
        return AuthResponse.builder()
                .token(token)
                .email(user.getEmail())
                .name(user.getName())
                .role(user.getRole().name())
                .build();
    }
}
