package com.bookpulse.bookpulse_api.controller;

import com.bookpulse.bookpulse_api.dto.AuthResponse;
import com.bookpulse.bookpulse_api.dto.LoginRequest;
import com.bookpulse.bookpulse_api.dto.RegisterRequest;
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
    public ResponseEntity<AuthResponse> register(@RequestBody RegisterRequest request) {
        return ResponseEntity.ok(authService.register(request));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@RequestBody LoginRequest request) {
        return ResponseEntity.ok(authService.login(request));
    }
}
