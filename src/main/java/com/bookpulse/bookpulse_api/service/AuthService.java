package com.bookpulse.bookpulse_api.service;

import com.bookpulse.bookpulse_api.dto.AuthResponseDTO;
import com.bookpulse.bookpulse_api.dto.LoginRequestDTO;
import com.bookpulse.bookpulse_api.dto.RegisterRequestDTO;
import com.bookpulse.bookpulse_api.exception.InvalidCredentialsException;
import com.bookpulse.bookpulse_api.exception.UserAlreadyExistsException;
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
    public AuthResponseDTO register(RegisterRequestDTO request) {
        // Verificamos si el email ya existe
        if (userRepository.existsByEmail(request.getEmail())) {
            throw new UserAlreadyExistsException("El correo electrónico ya está registrado");
        }

        User user = new User();
        user.setEmail(request.getEmail());
        // ¡Crucial! Encriptamos la contraseña con BCrypt antes de guardarla en Neon
        user.setPassword(passwordEncoder.encode(request.getPassword()));
        user.setName(request.getName());
        user.setRole(Role.ROLE_CLIENT); // Por defecto, el registro web es para clientes

        userRepository.save(user);

        String token = jwtService.generateToken(user);
        return AuthResponseDTO.builder()
                .token(token)
                .email(user.getEmail())
                .name(user.getName())
                .role(user.getRole().name())
                .build();
    }

    /**
     * Autentica a un usuario y le devuelve su token JWT si las credenciales son válidas.
     */
    public AuthResponseDTO login(LoginRequestDTO request) {
        User user = userRepository.findByEmail(request.getEmail())
                .orElseThrow(() -> new InvalidCredentialsException("Correo electrónico o contraseña incorrectos"));

        // Comparamos la contraseña en texto plano con el hash encriptado de la BD
        if (!passwordEncoder.matches(request.getPassword(), user.getPassword())) {
            throw new InvalidCredentialsException("Correo electrónico o contraseña incorrectos");
        }

        String token = jwtService.generateToken(user);
        return AuthResponseDTO.builder()
                .token(token)
                .email(user.getEmail())
                .name(user.getName())
                .role(user.getRole().name())
                .build();
    }
}
