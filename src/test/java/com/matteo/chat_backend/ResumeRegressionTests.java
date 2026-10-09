package com.matteo.chat_backend;

import com.matteo.chat_backend.entity.*;
import com.matteo.chat_backend.repository.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
@WithMockUser(username = "resume@example.com")
class ResumeRegressionTests {
    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired ServerRepository servers;
    @Autowired ServerMemberRepository members;
    @Autowired ChannelRepository channels;
    @Autowired DirectMessageRepository dms;
    @Autowired MessageRepository messages;

    private User me;
    private User other;
    private Server server;
    private Channel channel;
    private final LocalDateTime now = LocalDateTime.of(2026, 10, 9, 12, 0);

    @BeforeEach
    void seed() {
        me = user("resume@example.com");
        other = user("other@example.com");
        server = new Server();
        server.setName("Resume");
        server.setOwner(me);
        server.setCreatedAt(now);
        servers.saveAndFlush(server);
        ServerMember member = new ServerMember();
        member.setServer(server);
        member.setUser(me);
        member.setRole("owner");
        member.setJoinedAt(now);
        members.saveAndFlush(member);
        channel = new Channel();
        channel.setServer(server);
        channel.setName("general");
        channel.setCreatedAt(now);
        channels.saveAndFlush(channel);
    }

    @Test
    void nativeClientsStillReceiveArraysFromAllListEndpoints() throws Exception {
        for (String path : List.of("/api/friends", "/api/friends/pending", "/api/friends/dm",
                "/api/servers/me", "/api/servers/invitations/pending",
                "/api/servers/" + server.getId() + "/channels",
                "/api/servers/" + server.getId() + "/members")) {
            mvc.perform(get(path)).andExpect(status().isOk())
                    .andExpect(jsonPath("$").isArray());
            mvc.perform(get(path).param("paged", "true")).andExpect(status().isOk())
                    .andExpect(jsonPath("$.content").isArray());
        }
    }

    @Test
    void paginationIsOptInAndWorksBeyondFirstDmPage() throws Exception {
        dm(me, other, now.minusMinutes(3));
        DirectMessage middle = dm(other, me, now.minusMinutes(2));
        dm(me, other, now.minusMinutes(1));

        mvc.perform(get("/api/friends/dm").param("paged", "true")
                        .param("page", "1").param("size", "1").param("sort", "createdAt,asc"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalElements").value(3))
                .andExpect(jsonPath("$.pageNumber").value(1))
                .andExpect(jsonPath("$.content.length()").value(1))
                .andExpect(jsonPath("$.content[0].id").value(middle.getId().toString()));
        mvc.perform(get("/api/friends").param("paged", "true"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.content").isArray());
    }

    @Test
    void channelAndDmCursorsFilterDatesAndDeletedMessages() {
        DirectMessage dm = dm(me, other, now);
        Message olderChannel = message(channel, null, now.minusMinutes(1), false);
        Message newerChannel = message(channel, null, now.plusMinutes(1), false);
        message(channel, null, now.minusMinutes(2), true);
        Message olderDm = message(null, dm, now.minusMinutes(1), false);
        Message newerDm = message(null, dm, now.plusMinutes(1), false);
        message(null, dm, now.plusMinutes(2), true);
        var page = PageRequest.of(0, 50);

        assertEquals(List.of(olderChannel.getId()), messages
                .findByChannelIdAndCreatedAtBeforeOrderByCreatedAtDesc(channel.getId(), now, page)
                .stream().map(Message::getId).toList());
        assertEquals(List.of(newerChannel.getId()), messages
                .findByChannelIdAndCreatedAtAfterOrderByCreatedAtAsc(channel.getId(), now, page)
                .stream().map(Message::getId).toList());
        assertEquals(List.of(olderDm.getId()), messages
                .findByDirectMessageIdAndCreatedAtBeforeOrderByCreatedAtDesc(dm.getId(), now, page)
                .stream().map(Message::getId).toList());
        assertEquals(List.of(newerDm.getId()), messages
                .findByDirectMessageIdAndCreatedAtAfterOrderByCreatedAtAsc(dm.getId(), now, page)
                .stream().map(Message::getId).toList());
    }

    @Test
    void deletedMembershipRemovesServerAndBulkChannelDeleteExecutes() {
        ServerMember member = members.findByServerAndUser(server, me).orElseThrow();
        member.setDeletedAt(now);
        members.saveAndFlush(member);
        assertTrue(servers.findServersByMember(me, PageRequest.of(0, 20, Sort.by("createdAt"))).isEmpty());
        assertFalse(members.existsByServerAndUser(server, me));
        channels.softDeleteByServerId(server.getId());
        channels.flush();
        assertTrue(channels.findActiveById(channel.getId()).isEmpty());
    }

    @Test
    void deletedMessageCannotBeRecoveredThroughContextEndpoint() throws Exception {
        Message hidden = message(channel, null, now, true);
        mvc.perform(get("/api/messages/context/" + hidden.getId()))
                .andExpect(status().isBadRequest());
    }

    @Test
    void corsDoesNotTrustEveryApplicationHostedOnRender() throws Exception {
        mvc.perform(options("/auth/login")
                        .header("Origin", "https://untrusted-app.onrender.com")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(status().isForbidden());
    }

    @Test
    void incorrectLoginReturnsUnauthorizedInsteadOfInternalServerError() throws Exception {
        mvc.perform(post("/auth/login").contentType("application/json")
                        .content("{\"email\":\"missing@example.com\",\"password\":\"wrong-password\"}"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Credenciales no válidas"));
    }

    private User user(String email) {
        User user = new User();
        user.setEmail(email);
        user.setUsername(email.substring(0, email.indexOf('@')));
        user.setPasswordHash("test-only");
        user.setCreatedAt(now);
        return users.saveAndFlush(user);
    }

    private DirectMessage dm(User first, User second, LocalDateTime time) {
        DirectMessage dm = new DirectMessage();
        dm.setUser1(first);
        dm.setUser2(second);
        dm.setCreatedAt(time);
        dm.setUser1LastReadAt(time);
        dm.setUser2LastReadAt(time);
        return dms.saveAndFlush(dm);
    }

    private Message message(Channel channel, DirectMessage dm, LocalDateTime time, boolean deleted) {
        Message message = new Message();
        message.setSender(me);
        message.setContent("test");
        message.setChannel(channel);
        message.setDirectMessage(dm);
        message.setCreatedAt(time);
        message.setDeletedAt(deleted ? now : null);
        return messages.saveAndFlush(message);
    }
}
