package com.matteo.chat_backend.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.crypto.Cipher;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;

@Service
public class MessageCryptoService {

    private static final String PREFIX = "enc:v1:";
    private static final int IV_BYTES = 12;
    private static final int TAG_BITS = 128;

    private final SecureRandom secureRandom = new SecureRandom();
    private final SecretKey key;

    public MessageCryptoService(@Value("${security.message-encryption-key}") String encodedKey) {
        try {
            byte[] keyBytes = Base64.getDecoder().decode(encodedKey);
            if (keyBytes.length != 32) {
                throw new IllegalArgumentException("MESSAGE_ENCRYPTION_KEY debe contener exactamente 32 bytes en Base64");
            }
            this.key = new SecretKeySpec(keyBytes, "AES");
        } catch (IllegalArgumentException exception) {
            throw new IllegalStateException("MESSAGE_ENCRYPTION_KEY no es una clave AES-256 Base64 valida", exception);
        }
    }

    public String encrypt(String plaintext) {
        try {
            byte[] iv = new byte[IV_BYTES];
            secureRandom.nextBytes(iv);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.ENCRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            byte[] ciphertext = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));
            return PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(iv)
                    + ":" + Base64.getUrlEncoder().withoutPadding().encodeToString(ciphertext);
        } catch (GeneralSecurityException exception) {
            throw new IllegalStateException("No se pudo cifrar el mensaje", exception);
        }
    }

    public String decrypt(String storedContent) {
        if (!isEncrypted(storedContent)) {
            return storedContent;
        }
        try {
            String[] parts = storedContent.split(":", 4);
            if (parts.length != 4) {
                throw new IllegalArgumentException("Formato cifrado no valido");
            }
            byte[] iv = Base64.getUrlDecoder().decode(parts[2]);
            byte[] ciphertext = Base64.getUrlDecoder().decode(parts[3]);
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");
            cipher.init(Cipher.DECRYPT_MODE, key, new GCMParameterSpec(TAG_BITS, iv));
            return new String(cipher.doFinal(ciphertext), StandardCharsets.UTF_8);
        } catch (GeneralSecurityException | IllegalArgumentException exception) {
            throw new IllegalStateException("No se pudo descifrar un mensaje almacenado", exception);
        }
    }

    public boolean isEncrypted(String content) {
        return content != null && content.startsWith(PREFIX);
    }
}
