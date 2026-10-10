package com.matteo.chat_backend;

import com.matteo.chat_backend.entity.*;
import com.matteo.chat_backend.repository.*;
import com.matteo.chat_backend.service.*;
import com.matteo.chat_backend.dto.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.UUID;
import static org.junit.jupiter.api.Assertions.*;

@SpringBootTest(properties = {"spring.jpa.properties.hibernate.dialect=org.hibernate.dialect.H2Dialect",
        "spring.datasource.url=jdbc:h2:mem:channel_activity;MODE=PostgreSQL;DB_CLOSE_DELAY=-1"})
@ActiveProfiles("test")
@Transactional
class ChannelActivityTests {
    @Autowired ChannelActivityService activity;
    @Autowired MessageService messages;
    @Autowired UserRepository users;
    @Autowired ServerRepository servers;
    @Autowired ChannelRepository channels;
    @Autowired ServerMemberRepository members;
    @Autowired jakarta.persistence.EntityManager em;
    @MockitoBean AttachmentStorageService storage;
    User sender, receiver, outsider;
    Channel channel;
    Server server;

    @BeforeEach void seed() {
        sender = user("Pedro"); receiver = user("Ana María"); outsider = user("Fuera");
        server = new Server(); server.setName("Equipo"); server.setOwner(sender);
        server.setCreatedAt(LocalDateTime.now().minusDays(1)); server = servers.saveAndFlush(server);
        channel = new Channel(); channel.setName("general"); channel.setServer(server);
        channel.setCreatedAt(server.getCreatedAt()); channel = channels.saveAndFlush(channel);
        join(sender); join(receiver);
    }

    @Test void countsAreDurableExcludeOwnMessagesAndRetriesAndResolveOnlyMembers() {
        var first = send("Hola @" + receiver.getUsername() + ", y @" + outsider.getUsername(), sender);
        em.flush(); em.clear();
        assertEquals(java.util.List.of(receiver.getId()), messages.getMessage(first.getId(), receiver.getEmail()).getMentionedUserIds());
        assertCounts(receiver, 1, 1); assertCounts(sender, 0, 0);
        var request = request("Segundo", sender); var second = messages.saveMessage(request, sender.getEmail());
        assertEquals(second.getId(), messages.saveMessage(request, sender.getEmail()).getId());
        send("Mi propio mensaje", receiver);
        assertCounts(receiver, 2, 1);
        assertTrue(activity.activity(outsider.getEmail()).isEmpty());
    }

    @Test void readWatermarkMovesOnlyForwardAndRejectsOtherChannelsAndOutsiders() {
        var first = send("Primero", sender);
        var second = send("Hola @" + receiver.getUsername(), sender);
        activity.markRead(channel.getId(), first.getId(), receiver.getEmail());
        assertCounts(receiver, 1, 1);
        activity.markRead(channel.getId(), second.getId(), receiver.getEmail());
        activity.markRead(channel.getId(), first.getId(), receiver.getEmail());
        em.clear(); assertCounts(receiver, 0, 0);
        assertThrows(org.springframework.security.access.AccessDeniedException.class,
                () -> activity.markRead(channel.getId(), second.getId(), outsider.getEmail()));
        var other = new Channel(); other.setName("otro"); other.setServer(server); other = channels.saveAndFlush(other);
        var otherId = other.getId();
        assertThrows(IllegalArgumentException.class, () -> activity.markRead(otherId, second.getId(), receiver.getEmail()));
    }

    @Test void editsAndDeletionReconcileMentionAndUnreadCounts() {
        var first = send("Normal", sender);
        messages.editMessage(first.getId(), "Para @" + receiver.getUsername(), sender.getEmail());
        assertCounts(receiver, 1, 1);
        messages.editMessage(first.getId(), "Sin mención", sender.getEmail());
        assertCounts(receiver, 1, 0);
        var second = send("@" + receiver.getUsername(), sender);
        messages.deleteMessage(second.getId(), sender.getEmail());
        assertCounts(receiver, 1, 0);
        members.findByServerAndUser(server, receiver).ifPresent(member -> { member.setDeletedAt(LocalDateTime.now()); members.save(member); });
        em.flush();
        assertTrue(activity.activity(receiver.getEmail()).isEmpty());
    }

    private void assertCounts(User user, long unread, long mentions) {
        em.flush();
        var summary = activity.activity(user.getEmail()).stream().filter(a -> a.channelId().equals(channel.getId())).findFirst().orElseThrow();
        assertEquals(unread, summary.unreadCount()); assertEquals(mentions, summary.mentionCount());
    }
    private User user(String name) {
        var user = new User(); user.setUsername(name + UUID.randomUUID().toString().substring(0, 6));
        user.setEmail(UUID.randomUUID() + "@example.invalid"); user.setPasswordHash("test-only");
        return users.saveAndFlush(user);
    }
    private void join(User user) {
        var member = new ServerMember(); member.setUser(user); member.setServer(server);
        member.setJoinedAt(server.getCreatedAt()); members.saveAndFlush(member);
    }
    private MessageRequest request(String text, User user) {
        var request = new MessageRequest(); request.setContent(text); request.setChannelId(channel.getId());
        request.setClientMessageId(UUID.randomUUID()); return request;
    }
    private MessageResponse send(String text, User user) { return messages.saveMessage(request(text, user), user.getEmail()); }
}
