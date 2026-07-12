package com.matteo.chat_backend.service;

import com.matteo.chat_backend.dto.AuthResponse;
import com.matteo.chat_backend.dto.ChangePasswordRequest;
import com.matteo.chat_backend.dto.LoginRequest;
import com.matteo.chat_backend.dto.RegisterRequest;
import com.matteo.chat_backend.entity.AuthSession;
import com.matteo.chat_backend.entity.User;
import com.matteo.chat_backend.repository.AuthSessionRepository;
import com.matteo.chat_backend.repository.UserRepository;
import com.matteo.chat_backend.security.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.HexFormat;

@Service
@RequiredArgsConstructor
public class AuthService {

    private static final int REFRESH_TOKEN_BYTES = 32;

    private final UserRepository userRepository;
    private final AuthSessionRepository authSessionRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthenticationManager authenticationManager;
    private final SecureRandom secureRandom = new SecureRandom();

    @Transactional
    public AuthResponse register(RegisterRequest request) {
        if (userRepository.existsByEmail(request.getEmail())) throw new IllegalArgumentException("El email ya esta en uso");
        if (userRepository.existsByUsername(request.getUsername())) throw new IllegalArgumentException("El username ya esta en uso");

        User user = new User();
        user.setUsername(request.getUsername().trim());
        user.setEmail(request.getEmail().trim().toLowerCase());
        user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        user.setIsOnline(false);
        user.setTokenVersion(0);
        user.setCreatedAt(LocalDateTime.now());
        userRepository.save(user);
        return createSession(user, false, "Registro");
    }

    @Transactional
    public AuthResponse login(LoginRequest request) {
        String email = request.getEmail().trim().toLowerCase();
        authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(email, request.getPassword()));
        User user = userRepository.findByEmail(email).orElseThrow(() -> new AccessDeniedException("Credenciales no validas"));
        user.setLastSeen(LocalDateTime.now());
        userRepository.save(user);
        return createSession(user, Boolean.TRUE.equals(request.getRememberMe()), cleanDeviceName(request.getDeviceName()));
    }

    @Transactional
    public AuthResponse refresh(String refreshToken) {
        AuthSession session = authSessionRepository.findByTokenHash(hash(refreshToken))
                .orElseThrow(() -> new AccessDeniedException("Sesion no valida"));
        LocalDateTime now = LocalDateTime.now();
        if (session.getRevokedAt() != null || session.getExpiresAt().isBefore(now)) {
            throw new AccessDeniedException("Sesion caducada");
        }
        String rotatedToken = randomToken();
        session.setTokenHash(hash(rotatedToken));
        session.setLastUsedAt(now);
        authSessionRepository.save(session);
        return response(session.getUser(), rotatedToken);
    }

    @Transactional
    public void logout(String refreshToken) {
        authSessionRepository.findByTokenHash(hash(refreshToken)).ifPresent(session -> {
            session.setRevokedAt(LocalDateTime.now());
            authSessionRepository.save(session);
        });
    }

    @Transactional
    public void changePassword(ChangePasswordRequest request, String email) {
        User user = userRepository.findByEmail(email).orElseThrow(() -> new AccessDeniedException("Usuario no autorizado"));
        if (!passwordEncoder.matches(request.getCurrentPassword(), user.getPasswordHash())) {
            throw new AccessDeniedException("La contrasena actual no es correcta");
        }
        if (passwordEncoder.matches(request.getNewPassword(), user.getPasswordHash())) {
            throw new IllegalArgumentException("La nueva contrasena debe ser diferente");
        }
        user.setPasswordHash(passwordEncoder.encode(request.getNewPassword()));
        user.setTokenVersion(user.getTokenVersion() + 1);
        userRepository.save(user);
        revokeAll(user);
    }

    private AuthResponse createSession(User user, boolean persistent, String deviceName) {
        String refreshToken = randomToken();
        LocalDateTime now = LocalDateTime.now();
        AuthSession session = new AuthSession();
        session.setUser(user);
        session.setTokenHash(hash(refreshToken));
        session.setDeviceName(deviceName);
        session.setPersistent(persistent);
        session.setCreatedAt(now);
        session.setLastUsedAt(now);
        session.setExpiresAt(now.plusDays(persistent ? 30 : 1));
        authSessionRepository.save(session);
        return response(user, refreshToken);
    }

    private AuthResponse response(User user, String refreshToken) {
        return new AuthResponse(jwtService.generateToken(user), refreshToken, user.getUsername(), user.getEmail());
    }

    private void revokeAll(User user) {
        LocalDateTime now = LocalDateTime.now();
        authSessionRepository.findByUserAndRevokedAtIsNullOrderByLastUsedAtDesc(user).forEach(session -> {
            session.setRevokedAt(now);
            authSessionRepository.save(session);
        });
    }

    private String randomToken() {
        byte[] bytes = new byte[REFRESH_TOKEN_BYTES];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String hash(String token) {
        if (token == null || token.isBlank()) throw new AccessDeniedException("Sesion no valida");
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 no disponible", exception);
        }
    }

    private String cleanDeviceName(String deviceName) {
        return deviceName == null || deviceName.isBlank() ? "Dispositivo" : deviceName.trim();
    }
}
