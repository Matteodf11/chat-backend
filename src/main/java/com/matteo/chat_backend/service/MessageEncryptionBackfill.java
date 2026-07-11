package com.matteo.chat_backend.service;

import com.matteo.chat_backend.repository.MessageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
@RequiredArgsConstructor
public class MessageEncryptionBackfill {

    private final MessageRepository messageRepository;
    private final MessageCryptoService cryptoService;

    @EventListener(ApplicationReadyEvent.class)
    @Transactional
    public void encryptLegacyMessages() {
        messageRepository.findAll().stream()
                .filter(message -> !cryptoService.isEncrypted(message.getContent()))
                .forEach(message -> message.setContent(cryptoService.encrypt(message.getContent())));
    }
}
