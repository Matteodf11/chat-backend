package com.matteo.chat_backend.service;

import com.matteo.chat_backend.entity.Server;
import com.matteo.chat_backend.entity.ServerMember;
import com.matteo.chat_backend.entity.User;
import com.matteo.chat_backend.repository.ChannelRepository;
import com.matteo.chat_backend.repository.MessageRepository;
import com.matteo.chat_backend.repository.ServerInvitationRepository;
import com.matteo.chat_backend.repository.ServerMemberRepository;
import com.matteo.chat_backend.repository.ServerRepository;
import com.matteo.chat_backend.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ServerServiceTest {

    @Mock ServerRepository serverRepository;
    @Mock ServerMemberRepository serverMemberRepository;
    @Mock ChannelRepository channelRepository;
    @Mock UserRepository userRepository;
    @Mock PresenceService presenceService;
    @Mock MessageRepository messageRepository;
    @Mock ServerInvitationRepository serverInvitationRepository;
    @Mock AvatarStorageService avatarStorageService;
    @Mock SimpMessagingTemplate messagingTemplate;

    private ServerService service;
    private Server server;
    private User owner;
    private User member;

    @BeforeEach
    void setUp() {
        service = new ServerService(
                serverRepository,
                serverMemberRepository,
                channelRepository,
                userRepository,
                presenceService,
                messageRepository,
                serverInvitationRepository,
                avatarStorageService,
                messagingTemplate
        );
        owner = user("owner@example.com");
        member = user("member@example.com");
        server = new Server();
        server.setId(UUID.randomUUID());
        server.setOwner(owner);
    }

    @Test
    void ownerCanRemoveMemberAndTargetReceivesEvent() {
        ServerMember ownerMembership = membership(owner, "owner");
        ServerMember targetMembership = membership(member, "member");
        targetMembership.setId(UUID.randomUUID());
        stubActorAndTarget(owner, member, ownerMembership, targetMembership);

        service.removeMember(server.getId(), member.getId(), owner.getEmail());

        verify(serverMemberRepository).save(org.mockito.ArgumentMatchers.argThat(m ->
                m.getId() != null && m.getId().equals(targetMembership.getId())
                        && m.getDeletedAt() != null
        ));
        verify(serverMemberRepository).flush();
        verify(messagingTemplate).convertAndSendToUser(
                eq(member.getEmail()), eq("/queue/server-events"),
                org.mockito.ArgumentMatchers.argThat(event ->
                        event instanceof com.matteo.chat_backend.dto.ServerEventResponse response
                                && "MEMBER_REMOVED".equals(response.getType())
                                && server.getId().equals(response.getServerId()))
        );
    }

    @Test
    void adminCannotRemoveAnotherAdmin() {
        User admin = user("admin@example.com");
        ServerMember actorMembership = membership(admin, "admin");
        ServerMember targetMembership = membership(member, "admin");
        stubActorAndTarget(admin, member, actorMembership, targetMembership);

        assertThrows(RuntimeException.class,
                () -> service.removeMember(server.getId(), member.getId(), admin.getEmail()));

        verify(serverMemberRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void onlyOwnerCanPromoteMember() {
        User admin = user("admin@example.com");
        ServerMember actorMembership = membership(admin, "admin");
        when(userRepository.findByEmail(admin.getEmail())).thenReturn(Optional.of(admin));
        when(serverRepository.findActiveById(server.getId())).thenReturn(Optional.of(server));
        when(serverMemberRepository.findByServerAndUser(server, admin)).thenReturn(Optional.of(actorMembership));

        assertThrows(RuntimeException.class,
                () -> service.updateMemberRole(server.getId(), member.getId(), "admin", admin.getEmail()));
    }

    @Test
    void ownerCanPromoteMember() {
        ServerMember ownerMembership = membership(owner, "owner");
        ServerMember targetMembership = membership(member, "member");
        stubActorAndTarget(owner, member, ownerMembership, targetMembership);
        when(serverMemberRepository.save(targetMembership)).thenReturn(targetMembership);

        var response = service.updateMemberRole(server.getId(), member.getId(), "admin", owner.getEmail());

        assertEquals("admin", response.getRole());
        verify(serverMemberRepository).save(targetMembership);
    }

    private void stubActorAndTarget(
            User actor,
            User target,
            ServerMember actorMembership,
            ServerMember targetMembership
    ) {
        when(userRepository.findByEmail(actor.getEmail())).thenReturn(Optional.of(actor));
        when(serverRepository.findActiveById(server.getId())).thenReturn(Optional.of(server));
        when(userRepository.findActiveById(target.getId())).thenReturn(Optional.of(target));
        when(serverMemberRepository.findByServerAndUser(server, actor)).thenReturn(Optional.of(actorMembership));
        when(serverMemberRepository.findByServerAndUser(server, target)).thenReturn(Optional.of(targetMembership));
    }

    private ServerMember membership(User user, String role) {
        ServerMember membership = new ServerMember();
        membership.setServer(server);
        membership.setUser(user);
        membership.setRole(role);
        return membership;
    }

    private User user(String email) {
        User user = new User();
        user.setId(UUID.randomUUID());
        user.setEmail(email);
        user.setUsername(email.substring(0, email.indexOf('@')));
        return user;
    }
}
