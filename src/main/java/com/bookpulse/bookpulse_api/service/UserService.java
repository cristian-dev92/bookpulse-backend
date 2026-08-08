package com.bookpulse.bookpulse_api.service;

import com.bookpulse.bookpulse_api.dto.ChangePasswordRequestDTO;
import com.bookpulse.bookpulse_api.dto.UpdateProfileRequestDTO;
import com.bookpulse.bookpulse_api.dto.UserProfileResponseDTO;
import com.bookpulse.bookpulse_api.model.User;
import com.bookpulse.bookpulse_api.repository.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class UserService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;

    public UserService(UserRepository userRepository, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Obtiene la información del perfil del usuario autenticado.
     */
    public UserProfileResponseDTO getUserProfile(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado"));

        return UserProfileResponseDTO.builder()
                .id(user.getId())
                .name(user.getName())
                .email(user.getEmail())
                .phone(user.getPhone())
                .role(user.getRole())
                .build();
    }

    /**
     * Actualiza los datos personales (nombre, teléfono).
     */
    @Transactional
    public UserProfileResponseDTO updateProfile(String email, UpdateProfileRequestDTO dto) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado"));

        user.setName(dto.getName());
        user.setPhone(dto.getPhone());

        User updatedUser = userRepository.save(user);

        return UserProfileResponseDTO.builder()
                .id(updatedUser.getId())
                .name(updatedUser.getName())
                .email(updatedUser.getEmail())
                .phone(updatedUser.getPhone())
                .role(updatedUser.getRole())
                .build();
    }

    /**
     * Valida la contraseña actual y actualiza por la nueva contraseña cifrada.
     */
    @Transactional
    public void changePassword(String email, ChangePasswordRequestDTO dto) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado"));

        // Validar contraseña actual
        if (!passwordEncoder.matches(dto.getCurrentPassword(), user.getPassword())) {
            throw new IllegalArgumentException("La contraseña actual es incorrecta");
        }

        // Cifrar e intentar actualizar la nueva contraseña
        user.setPassword(passwordEncoder.encode(dto.getNewPassword()));
        userRepository.save(user);
    }

    /**
     * Permite al Administrador actualizar los datos (nombre, email, teléfono) de cualquier usuario por su ID.
     */
    @Transactional
    public UserProfileResponseDTO updateUserByAdmin(Long id, UpdateProfileRequestDTO dto) {
        User user = userRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Usuario no encontrado con ID: " + id));

        // Validación: si se cambia el email, verificar que no pertenezca a otro usuario
        if (dto.getEmail() != null && !dto.getEmail().equalsIgnoreCase(user.getEmail())) {
            if (userRepository.existsByEmail(dto.getEmail())) {
                throw new IllegalArgumentException("El email ya está en uso por otro usuario");
            }
            user.setEmail(dto.getEmail());
        }

        user.setName(dto.getName());

        // Si el DTO incluye teléfono, se actualiza también
        if (dto.getPhone() != null) {
            user.setPhone(dto.getPhone());
        }

        User updatedUser = userRepository.save(user);

        return UserProfileResponseDTO.builder()
                .id(updatedUser.getId())
                .name(updatedUser.getName())
                .email(updatedUser.getEmail())
                .phone(updatedUser.getPhone())
                .role(updatedUser.getRole())
                .build();
    }

}