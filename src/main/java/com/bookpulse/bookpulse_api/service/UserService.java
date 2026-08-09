package com.bookpulse.bookpulse_api.service;

import com.bookpulse.bookpulse_api.dto.*;
import com.bookpulse.bookpulse_api.model.Role; // Asegúrate de importar tu Enum/Clase Role si existe
import com.bookpulse.bookpulse_api.model.User;
import com.bookpulse.bookpulse_api.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    // ==========================================
    // METODOS DE PERFIL DE USUARIO
    // ==========================================

    /**
     * Obtiene la información del perfil del usuario autenticado.
     */
    public UserResponseDTO getUserProfile(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado"));

        return mapToDTO(user);
    }

    /**
     * Actualiza los datos personales (nombre, teléfono).
     */
    @Transactional
    public UserResponseDTO updateProfile(String email, UpdateProfileRequestDTO dto) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado"));

        user.setName(dto.getName());
        user.setPhone(dto.getPhone());

        User updatedUser = userRepository.save(user);
        return mapToDTO(updatedUser);
    }

    /**
     * Valida la contraseña actual y actualiza por la nueva contraseña cifrada.
     */
    @Transactional
    public void changePassword(String email, ChangePasswordRequestDTO dto) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado"));

        if (!passwordEncoder.matches(dto.getCurrentPassword(), user.getPassword())) {
            throw new IllegalArgumentException("La contraseña actual es incorrecta");
        }

        user.setPassword(passwordEncoder.encode(dto.getNewPassword()));
        userRepository.save(user);
    }

    // ==========================================
    // MÉTODOS DE ADMINISTRACIÓN (ADMIN)
    // ==========================================

    /**
     * Devuelve el listado completo de todos los usuarios registrados.
     */
    public List<UserResponseDTO> findAllUsers() {
        return userRepository.findAll().stream()
                .map(this::mapToDTO)
                .collect(Collectors.toList());
    }

    /**
     * Permite al Administrador cambiar únicamente el rol de un usuario.
     */
    @Transactional
    public UserResponseDTO updateRole(Long id, String newRoleStr) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado con ID: " + id));

        try {
            // Si tu entidad usa Enum Role, descomenta la siguiente línea:
            user.setRole(Role.valueOf(newRoleStr.toUpperCase()));

            // Si en tu entidad role es un String, usa:
            // user.setRole(newRoleStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Rol no válido: " + newRoleStr);
        }

        User updatedUser = userRepository.save(user);
        return mapToDTO(updatedUser);
    }

    /**
     * Permite al Administrador una edición completa (Nombre, Email, Teléfono, Rol y Contraseña opcional).
     */
    @Transactional
    public UserResponseDTO adminUpdateUser(Long id, AdminUpdateUserRequestDTO dto) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado con ID: " + id));

        // 1. Validar y actualizar email
        if (dto.getEmail() != null && !dto.getEmail().equalsIgnoreCase(user.getEmail())) {
            if (userRepository.existsByEmail(dto.getEmail())) {
                throw new IllegalArgumentException("El email ya está en uso por otro usuario");
            }
            user.setEmail(dto.getEmail());
        }

        // 2. Actualizar campos básicos
        if (dto.getName() != null) {
            user.setName(dto.getName());
        }
        if (dto.getPhone() != null) {
            user.setPhone(dto.getPhone());
        }

        // 3. Actualizar Rol (Directamente asignando el Enum si dto.getRole() devuelve Role)
        if (dto.getRole() != null) {
            user.setRole(Role.valueOf(dto.getRole()));
            // Si en tu DTO role sigue siendo un String, usa en su lugar:
            // user.setRole(Role.valueOf(dto.getRole().toString().trim().toUpperCase()));
        }

        // 4. Actualizar contraseña si se ha proporcionado una nueva
        if (dto.getNewPassword() != null && !dto.getNewPassword().trim().isEmpty()) {
            user.setPassword(passwordEncoder.encode(dto.getNewPassword()));
        }

        // 5. Guardar en BD
        User updatedUser = userRepository.saveAndFlush(user);
        return mapToDTO(updatedUser);
    }

    /**
     * Permite al Administrador eliminar a un usuario por su ID.
     */
    @Transactional
    public void deleteUserById(Long id) {
        if (!userRepository.existsById(id)) {
            throw new IllegalArgumentException("Usuario no encontrado con ID: " + id);
        }
        userRepository.deleteById(id);
    }

    // ==========================================
    // MÉTODO AUXILIAR MAPPER
    // ==========================================

    private UserResponseDTO mapToDTO(User user) {
        return UserResponseDTO.builder()
                .id(user.getId())
                .name(user.getName())
                .email(user.getEmail())
                .phone(user.getPhone())
                .role(user.getRole() != null ? user.getRole() : Role.ROLE_CLIENT)
                .build();
    }
}