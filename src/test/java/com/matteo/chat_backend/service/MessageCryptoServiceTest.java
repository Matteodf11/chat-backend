package com.matteo.chat_backend.service;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;
import java.util.Base64;

class MessageCryptoServiceTest {

    private final MessageCryptoService cryptoService = new MessageCryptoService(
            "AAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAAA="
    );

    @Test
    void encryptsWithRandomIvAndDecrypts() {
        String first = cryptoService.encrypt("mensaje privado");
        String second = cryptoService.encrypt("mensaje privado");

        assertTrue(first.startsWith("enc:v1:"));
        assertNotEquals(first, second);
        assertEquals("mensaje privado", cryptoService.decrypt(first));
        assertEquals("mensaje privado", cryptoService.decrypt(second));
    }

    @Test
    void rejectsTamperedCiphertext() {
        String encrypted = cryptoService.encrypt("contenido");
        String[] parts = encrypted.split(":", 4);
        byte[] ciphertext = Base64.getUrlDecoder().decode(parts[3]);
        ciphertext[ciphertext.length - 1] ^= 1;
        String tampered = parts[0] + ":" + parts[1] + ":" + parts[2] + ":"
                + Base64.getUrlEncoder().withoutPadding().encodeToString(ciphertext);

        assertThrows(IllegalStateException.class, () -> cryptoService.decrypt(tampered));
    }

    @Test
    void readsLegacyPlaintextDuringMigration() {
        assertEquals("mensaje antiguo", cryptoService.decrypt("mensaje antiguo"));
    }
}
