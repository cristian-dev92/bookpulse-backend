package com.bookpulse.bookpulse_api.controller;

import com.bookpulse.bookpulse_api.dto.AuthResponseDTO;
import com.bookpulse.bookpulse_api.dto.LoginRequestDTO;
import com.bookpulse.bookpulse_api.dto.RegisterRequestDTO;
import com.bookpulse.bookpulse_api.service.AuthService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * Endpoints públicos para la gestión del ciclo de vida de los usuarios.
 * * @author Cristian
 */
@RestController
@RequestMapping("/api/v1/auth")
@RequiredArgsConstructor
@CrossOrigin(origins = "*") // Permite que Angular se conecte sin problemas de CORS
public class AuthController {

    private final AuthService authService;

    @PostMapping("/register")
    public ResponseEntity<AuthResponseDTO> register(@RequestBody RegisterRequestDTO request) {
        return ResponseEntity.ok(authService.register(request));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponseDTO> login(@RequestBody LoginRequestDTO request) {
        return ResponseEntity.ok(authService.login(request));
    }
}
