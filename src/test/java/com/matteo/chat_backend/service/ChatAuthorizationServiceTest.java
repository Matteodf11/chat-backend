package com.matteo.chat_backend.service;

import com.matteo.chat_backend.entity.Channel;
import com.matteo.chat_backend.entity.DirectMessage;
import com.matteo.chat_backend.entity.Server;
import com.matteo.chat_backend.entity.User;
import com.matteo.chat_backend.repository.ChannelRepository;
import com.matteo.chat_backend.repository.DirectMessageRepository;
import com.matteo.chat_backend.repository.ServerMemberRepository;
import com.matteo.chat_backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.access.AccessDeniedException;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class ChatAuthorizationServiceTest {

    @Mock UserRepository userRepository;
    @Mock ChannelRepository channelRepository;
    @Mock DirectMessageRepository directMessageRepository;
    @Mock ServerMemberRepository serverMemberRepository;

    private ChatAuthorizationService service;
    private User requester;

    @BeforeEach
    void setUp() {
        service = new ChatAuthorizationService(
                userRepository, channelRepository, directMessageRepository, serverMemberRepository
        );
        requester = new User();
        requester.setId(UUID.randomUUID());
        requester.setEmail("user@example.com");
        lenient().when(userRepository.findByEmail(requester.getEmail())).thenReturn(Optional.of(requester));
    }

    @Test
    void rejectsChannelForNonMember() {
        UUID channelId = UUID.randomUUID();
        Channel channel = new Channel();
        channel.setServer(new Server());
        when(channelRepository.findById(channelId)).thenReturn(Optional.of(channel));
        when(serverMemberRepository.existsByServerAndUser(channel.getServer(), requester)).thenReturn(false);

        assertThrows(AccessDeniedException.class,
                () -> service.requireChannelAccess(channelId, requester.getEmail()));
    }

    @Test
    void allowsDirectMessageParticipantAndRejectsStranger() {
        UUID dmId = UUID.randomUUID();
        User participant = new User();
        participant.setId(UUID.randomUUID());
        DirectMessage dm = new DirectMessage();
        dm.setUser1(requester);
        dm.setUser2(participant);
        when(directMessageRepository.findById(dmId)).thenReturn(Optional.of(dm));

        assertSame(dm, service.requireDirectMessageAccess(dmId, requester.getEmail()));

        User stranger = new User();
        stranger.setId(UUID.randomUUID());
        stranger.setEmail(requester.getEmail());
        when(userRepository.findByEmail(requester.getEmail())).thenReturn(Optional.of(stranger));
        assertThrows(AccessDeniedException.class,
                () -> service.requireDirectMessageAccess(dmId, requester.getEmail()));
    }

    @Test
    void rejectsSubscriptionToAnotherUsersFriendEvents() {
        assertThrows(AccessDeniedException.class, () ->
                service.requireSubscriptionAccess("/topic/friends." + UUID.randomUUID(), requester.getEmail()));
    }

    @Test
    void rejectsUnknownSubscriptionDestination() {
        assertThrows(AccessDeniedException.class, () ->
                service.requireSubscriptionAccess("/topic/private-data", requester.getEmail()));
    }
}
