package com.bookpulse.bookpulse_api.controller;

import com.bookpulse.bookpulse_api.dto.AdminUpdateUserRequestDTO;
import com.bookpulse.bookpulse_api.dto.ChangePasswordRequestDTO;
import com.bookpulse.bookpulse_api.dto.UpdateProfileRequestDTO;
import com.bookpulse.bookpulse_api.dto.UserResponseDTO;
import com.bookpulse.bookpulse_api.service.UserService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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
    public ResponseEntity<UserResponseDTO> getMyProfile(@AuthenticationPrincipal UserDetails userDetails) {

        if (userDetails == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }

        UserResponseDTO profile = userService.getUserProfile(userDetails.getUsername());
        return ResponseEntity.ok(profile);
    }

    // PUT /api/v1/users/me -> Actualizar datos personales (nombre, teléfono)
    @PutMapping("/me")
    public ResponseEntity<UserResponseDTO> updateMyProfile(
            @AuthenticationPrincipal UserDetails userDetails,
            @Valid @RequestBody UpdateProfileRequestDTO dto) {
        UserResponseDTO updatedProfile = userService.updateProfile(userDetails.getUsername(), dto);
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

    // PUT /api/v1/users/admin/{id} -> Actualizar datos de cualquier cliente desde el panel admin
    @PutMapping("/admin/{id}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<UserResponseDTO> updateUserByAdmin(
            @PathVariable Long id,
            @Valid @RequestBody AdminUpdateUserRequestDTO dto) {

        UserResponseDTO updatedUser = userService.adminUpdateUser(id, dto);
        return ResponseEntity.ok(updatedUser);
    }

}