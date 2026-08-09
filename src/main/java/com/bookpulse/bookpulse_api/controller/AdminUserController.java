package com.bookpulse.bookpulse_api.controller;

import com.bookpulse.bookpulse_api.dto.AdminUpdateUserRequestDTO;
import com.bookpulse.bookpulse_api.dto.UpdateUserRoleRequestDTO;
import com.bookpulse.bookpulse_api.dto.UserResponseDTO;
import com.bookpulse.bookpulse_api.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin/users")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')") // Protege todos los endpoints para que solo pase el ROL ADMIN
public class AdminUserController {

    private final UserService userService;

    // 1. Listar todos los usuarios
    @GetMapping
    public ResponseEntity<List<UserResponseDTO>> getAllUsers() {
        return ResponseEntity.ok(userService.findAllUsers());
    }

    // 2. Cambiar rol de un usuario (USER -> ADMIN o viceversa)
    @PatchMapping("/{id}/role")
    public ResponseEntity<UserResponseDTO> updateUserRole(
            @PathVariable Long id,
            @RequestBody UpdateUserRoleRequestDTO request) {
        return ResponseEntity.ok(userService.updateRole(id, request.getRole()));
    }

    // 3. Edición completa de un usuario por el Admin (PUT)
    @PutMapping("/{id}")
    public ResponseEntity<UserResponseDTO> updateUserFull(
            @PathVariable Long id,
            @RequestBody AdminUpdateUserRequestDTO request) { // DTO correspondiente
        return ResponseEntity.ok(userService.adminUpdateUser(id, request)); // Nombre exacto del método
    }

    // 4. Eliminar usuario
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteUser(@PathVariable Long id) {
        userService.deleteUserById(id);
        return ResponseEntity.noContent().build();
    }

}