package com.bookpulse.bookpulse_api.controller;

import com.bookpulse.bookpulse_api.dto.ChangePasswordRequestDTO;
import com.bookpulse.bookpulse_api.dto.UpdateProfileRequestDTO;
import com.bookpulse.bookpulse_api.dto.UserProfileResponseDTO;
import com.bookpulse.bookpulse_api.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.web.bind.annotation.*;

/**
 * Controlador REST para la gestión del perfil del usuario autenticado.
 *
 * @author Cristian
 * @since 1.0
 */
@RestController
@RequestMapping("/api/v1/users")
@CrossOrigin(origins = {"http://localhost:5173", "http://localhost:4200"})
public class UserController {

    private final UserService userService;

    public UserController(UserService userService) {
        this.userService = userService;
    }

    // GET /api/v1/users/me -> Obtener perfil del usuario autenticado
    @GetMapping("/me")
    public ResponseEntity<UserProfileResponseDTO> getMyProfile(@AuthenticationPrincipal UserDetails userDetails) {
        UserProfileResponseDTO profile = userService.getUserProfile(userDetails.getUsername());
        return ResponseEntity.ok(profile);
    }

    // PUT /api/v1/users/me -> Actualizar datos personales (nombre, teléfono)
    @PutMapping("/me")
    public ResponseEntity<UserProfileResponseDTO> updateMyProfile(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody UpdateProfileRequestDTO dto) {
        UserProfileResponseDTO updatedProfile = userService.updateProfile(userDetails.getUsername(), dto);
        return ResponseEntity.ok(updatedProfile);
    }

    // PUT /api/v1/users/me/password -> Cambiar contraseña
    @PutMapping("/me/password")
    public ResponseEntity<Void> changePassword(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody ChangePasswordRequestDTO dto) {
        userService.changePassword(userDetails.getUsername(), dto);
        return ResponseEntity.ok().build();
    }
}