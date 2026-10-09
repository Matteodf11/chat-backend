package com.matteo.chat_backend.config;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.util.StringUtils;

@Configuration
public class SecurityValidationConfig {

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Value("${security.message-encryption-key}")
    private String messageEncryptionKey;

    @PostConstruct
    public void validateSecrets() {
        // Validar JWT Secret: mínimo 32 caracteres para HS256 (256 bits)
        if (!StringUtils.hasText(jwtSecret) || jwtSecret.length() < 32) {
            throw new IllegalStateException(
                    "JWT_SECRET debe tener al menos 32 caracteres (256 bits para HS256). " +
                    "Genera uno con: openssl rand -base64 32"
            );
        }

        // Validar Message Encryption Key: debe ser Base64 válido de 32 bytes (44 chars Base64)
        if (!StringUtils.hasText(messageEncryptionKey)) {
            throw new IllegalStateException("MESSAGE_ENCRYPTION_KEY no configurada");
        }
        try {
            byte[] keyBytes = java.util.Base64.getDecoder().decode(messageEncryptionKey);
            if (keyBytes.length != 32) {
                throw new IllegalStateException(
                        "MESSAGE_ENCRYPTION_KEY debe decodificar a exactamente 32 bytes (AES-256). " +
                        "Genera una con: openssl rand -base64 32"
                );
            }
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("MESSAGE_ENCRYPTION_KEY no es Base64 válido", e);
        }
    }
}