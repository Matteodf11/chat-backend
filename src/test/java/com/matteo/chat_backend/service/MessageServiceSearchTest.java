package com.matteo.chat_backend.service;

import com.matteo.chat_backend.entity.Channel;
import com.matteo.chat_backend.entity.Message;
import com.matteo.chat_backend.entity.User;
import com.matteo.chat_backend.repository.MessageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MessageServiceSearchTest {

    @Mock MessageRepository messageRepository;
    @Mock ChatAuthorizationService authorizationService;
    @Mock MessageCryptoService cryptoService;
    @Mock RequestRateLimiter rateLimiter;
    @Mock AttachmentStorageService attachmentStorageService;

    private MessageService service;
    private UUID channelId;

    @BeforeEach
    void setUp() {
        service = new MessageService(
                messageRepository,
                org.mockito.Mockito.mock(com.matteo.chat_backend.repository.UserRepository.class),
                org.mockito.Mockito.mock(com.matteo.chat_backend.repository.DirectMessageRepository.class),
                authorizationService,
                cryptoService,
                rateLimiter,
                attachmentStorageService,
                org.mockito.Mockito.mock(ChannelActivityService.class)
        );
        channelId = UUID.randomUUID();
    }

    @Test
    void searchesDecryptedContentAfterAuthorizingChannel() {
        Channel channel = new Channel();
        channel.setId(channelId);
        User sender = new User();
        sender.setId(UUID.randomUUID());
        sender.setUsername("Matteo");
        Message matching = new Message();
        matching.setId(UUID.randomUUID());
        matching.setChannel(channel);
        matching.setSender(sender);
        matching.setContent("encrypted-content");
        matching.setEdited(false);
        matching.setCreatedAt(LocalDateTime.now());

        when(authorizationService.requireChannelAccess(channelId, "user@example.com")).thenReturn(channel);
        when(messageRepository.findByChannelIdAndCreatedAtBeforeOrderByCreatedAtDesc(
                eq(channelId), any(LocalDateTime.class), any(Pageable.class)))
                .thenReturn(List.of(matching));
        when(cryptoService.decrypt("encrypted-content")).thenReturn("Mensaje secreto para buscar");

        var results = service.searchChannelMessages(
                channelId, "secreto", "mat", null, null, false, 20, "user@example.com");

        assertEquals(1, results.size());
        assertEquals(matching.getId(), results.getFirst().getId());
    }

    @Test
    void deniedChannelIsNeverScanned() {
        when(authorizationService.requireChannelAccess(channelId, "intruder@example.com"))
                .thenThrow(new AccessDeniedException("No tienes acceso"));

        assertThrows(AccessDeniedException.class, () -> service.searchChannelMessages(
                channelId, "texto", "", null, null, false, 20, "intruder@example.com"));

        verify(messageRepository, never()).findByChannelIdAndCreatedAtBeforeOrderByCreatedAtDesc(
                any(), any(), any());
    }
}
