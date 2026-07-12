package com.matteo.chat_backend.controller;

import com.matteo.chat_backend.dto.AuthResponse;
import com.matteo.chat_backend.dto.LoginRequest;
import com.matteo.chat_backend.dto.RegisterRequest;
import com.matteo.chat_backend.dto.RefreshTokenRequest;
import com.matteo.chat_backend.dto.ChangePasswordRequest;
import com.matteo.chat_backend.service.AuthService;
import jakarta.validation.Valid;
import jakarta.servlet.http.HttpServletRequest;
import com.matteo.chat_backend.service.RequestRateLimiter;
import java.time.Duration;
import java.security.Principal;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final RequestRateLimiter rateLimiter;

    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(@Valid @RequestBody RegisterRequest request, HttpServletRequest servletRequest) {
        rateLimiter.requireCapacity("register:" + clientAddress(servletRequest), 5, Duration.ofHours(1), "Demasiados intentos de registro");
        return ResponseEntity.ok(authService.register(request));
    }

    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody LoginRequest request, HttpServletRequest servletRequest) {
        String address = clientAddress(servletRequest);
        rateLimiter.requireCapacity("login-ip:" + address, 30, Duration.ofMinutes(15), "Demasiados intentos de inicio de sesion");
        rateLimiter.requireCapacity("login-account:" + request.getEmail().toLowerCase(), 10, Duration.ofMinutes(15), "Demasiados intentos para esta cuenta");
        return ResponseEntity.ok(authService.login(request));
    }

    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(@Valid @RequestBody RefreshTokenRequest request) {
        return ResponseEntity.ok(authService.refresh(request.getRefreshToken()));
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@Valid @RequestBody RefreshTokenRequest request) {
        authService.logout(request.getRefreshToken());
        return ResponseEntity.noContent().build();
    }

    @PutMapping("/password")
    public ResponseEntity<Void> changePassword(
            @Valid @RequestBody ChangePasswordRequest request,
            Principal principal) {
        authService.changePassword(request, principal.getName());
        return ResponseEntity.noContent().build();
    }

    private String clientAddress(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",", 2)[0].trim();
        }
        return request.getRemoteAddr();
    }
}
