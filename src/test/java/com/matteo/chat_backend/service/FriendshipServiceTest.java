package com.matteo.chat_backend.service;

import com.matteo.chat_backend.entity.DirectMessage;
import com.matteo.chat_backend.entity.User;
import com.matteo.chat_backend.repository.DirectMessageRepository;
import com.matteo.chat_backend.repository.FriendshipRepository;
import com.matteo.chat_backend.repository.MessageRepository;
import com.matteo.chat_backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageRequest;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FriendshipServiceTest {

    @Mock FriendshipRepository friendshipRepository;
    @Mock UserRepository userRepository;
    @Mock DirectMessageRepository directMessageRepository;
    @Mock MessageRepository messageRepository;
    @Mock SimpMessagingTemplate messagingTemplate;
    @Mock PresenceService presenceService;

    private FriendshipService service;
    private User user1;
    private User user2;
    private DirectMessage dm;

    @BeforeEach
    void setUp() {
        service = new FriendshipService(
                friendshipRepository,
                userRepository,
                directMessageRepository,
                messageRepository,
                messagingTemplate,
                presenceService,
                org.mockito.Mockito.mock(MessageCryptoService.class), org.mockito.Mockito.mock(UserSafetyService.class)
        );
        user1 = user("one@example.com");
        user2 = user("two@example.com");
        dm = new DirectMessage();
        dm.setId(UUID.randomUUID());
        dm.setUser1(user1);
        dm.setUser2(user2);
        dm.setCreatedAt(LocalDateTime.now().minusDays(1));
        dm.setUser1LastReadAt(LocalDateTime.now().minusHours(1));
        dm.setUser2LastReadAt(LocalDateTime.now().minusMinutes(30));
    }

    @Test
    void dmIsUnreadWhenOtherUserSentANewerMessage() {
        when(userRepository.findByEmail(user1.getEmail())).thenReturn(Optional.of(user1));
        when(directMessageRepository.findAllByUser(user1, PageRequest.of(0, 20))).thenReturn(new org.springframework.data.domain.PageImpl<>(List.of(dm)));
        when(messageRepository.countUnreadDmMessages(dm.getId(), user1.getId(), dm.getUser1LastReadAt()))
                .thenReturn(1L);

        var result = service.getMyDms(user1.getEmail(), PageRequest.of(0, 20));

        assertTrue(result.content().getFirst().getHasUnread());
    }

    @Test
    void markingReadUpdatesOnlyCurrentParticipant() {
        LocalDateTime previousUser2Read = dm.getUser2LastReadAt();
        when(userRepository.findByEmail(user1.getEmail())).thenReturn(Optional.of(user1));
        when(directMessageRepository.lockActiveById(dm.getId())).thenReturn(Optional.of(dm));

        service.markDmRead(dm.getId(), user1.getEmail());

        assertTrue(dm.getUser1LastReadAt().isAfter(dm.getCreatedAt()));
        assertFalse(dm.getUser1LastReadAt().equals(previousUser2Read));
        assertTrue(dm.getUser2LastReadAt().equals(previousUser2Read));
        verify(directMessageRepository).save(dm);
    }

    private User user(String email) {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail(email);
        user.setUsername(email.substring(0, email.indexOf('@')));
        return user;
    }
}
