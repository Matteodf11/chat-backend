package com.matteo.chat_backend.service;

import com.matteo.chat_backend.dto.ChannelActivityResponse;
import com.matteo.chat_backend.dto.ServerEventResponse;
import com.matteo.chat_backend.entity.Message;
import com.matteo.chat_backend.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import java.util.*;
import java.util.regex.Pattern;

@Service
@RequiredArgsConstructor
public class ChannelActivityService {
    private final JdbcTemplate jdbc;
    private final ChatAuthorizationService authorization;
    private final UserRepository users;
    private final MessageRepository messages;
    private final ServerMemberRepository members;
    private final SimpMessagingTemplate messaging;

    @Transactional(readOnly = true)
    public List<ChannelActivityResponse> activity(String email) {
        var user = authorization.requireUser(email);
        // One aggregate query for all accessible channels; content remains encrypted.
        return jdbc.query("""
            SELECT c.id, c.server_id, COUNT(m.id) AS unread_count, COUNT(mm.user_id) AS mention_count
            FROM channels c JOIN servers s ON s.id = c.server_id AND s.deleted_at IS NULL
            JOIN server_members sm ON sm.server_id = c.server_id AND sm.user_id = ? AND sm.deleted_at IS NULL
            LEFT JOIN channel_reads r ON r.channel_id = c.id AND r.user_id = sm.user_id
            LEFT JOIN messages m ON m.channel_id = c.id AND m.deleted_at IS NULL AND m.sender_id <> sm.user_id
                AND m.created_at > COALESCE(sm.joined_at, c.created_at, TIMESTAMP '1970-01-01 00:00:00')
                AND (r.read_at IS NULL OR m.created_at > r.read_at)
            LEFT JOIN message_mentions mm ON mm.message_id = m.id AND mm.user_id = sm.user_id
            WHERE c.deleted_at IS NULL GROUP BY c.id, c.server_id
            """, (rs, row) -> new ChannelActivityResponse(rs.getObject(1, UUID.class), rs.getObject(2, UUID.class),
                rs.getLong(3), rs.getLong(4)), user.getId());
    }

    @Transactional
    public void markRead(UUID channelId, UUID messageId, String email) {
        var channel = authorization.requireChannelAccess(channelId, email);
        var user = authorization.requireUser(email);
        // Serialize concurrent reads across devices, never move the watermark backwards.
        users.lockActiveById(user.getId()).orElseThrow();
        var message = messages.findById(messageId).orElseThrow(() -> new IllegalArgumentException("Mensaje no disponible"));
        if (message.getChannel() == null || !message.getChannel().getId().equals(channelId)) {
            throw new IllegalArgumentException("El mensaje no pertenece a este canal");
        }
        int updated = jdbc.update("UPDATE channel_reads SET read_at = ? WHERE user_id = ? AND channel_id = ? AND read_at < ?",
                message.getCreatedAt(), user.getId(), channelId, message.getCreatedAt());
        if (updated == 0 && jdbc.queryForObject("SELECT COUNT(*) FROM channel_reads WHERE user_id = ? AND channel_id = ?",
                Long.class, user.getId(), channelId) == 0) {
            jdbc.update("INSERT INTO channel_reads(user_id, channel_id, read_at) VALUES (?, ?, ?)", user.getId(), channelId, message.getCreatedAt());
        }
        afterCommit(() -> messaging.convertAndSendToUser(email, "/queue/server-events",
                new ServerEventResponse("CHANNEL_ACTIVITY", channel.getServer().getId(), null)));
    }

    public void updateMentions(Message message, String content) {
        if (message.getChannel() == null) return;
        var server = message.getChannel().getServer();
        var recipients = members.findByServer(server);
        message.getMentionedUserIds().clear();
        if (message.getDeletedAt() == null && content.contains("@")) {
            var names = new LinkedHashMap<UUID, String>();
            recipients.forEach(member -> names.put(member.getUser().getId(), member.getUser().getUsername()));
            var resolved = resolveMentions(content, names);
            resolved.remove(message.getSender().getId());
            message.getMentionedUserIds().addAll(resolved);
        }
        var emails = recipients.stream().map(member -> member.getUser().getEmail()).distinct().toList();
        afterCommit(() -> emails.forEach(email -> messaging.convertAndSendToUser(email, "/queue/server-events",
                new ServerEventResponse("CHANNEL_ACTIVITY", server.getId(), null))));
    }

    static boolean containsMention(String content, String username) {
        return mentionPattern(username).matcher(content).find();
    }

    private static Pattern mentionPattern(String username) {
        return Pattern.compile("(?<![\\p{L}\\p{N}_@])@" + Pattern.quote(username) + "(?![\\p{L}\\p{N}_]|[.-][\\p{L}\\p{N}_])",
                Pattern.CASE_INSENSITIVE | Pattern.UNICODE_CASE);
    }

    static Set<UUID> resolveMentions(String content, Map<UUID, String> names) {
        var resolved = new LinkedHashSet<UUID>();
        var occupied = new boolean[content.length()];
        // Longest names win: @Ana María must not also notify another member called Ana.
        names.entrySet().stream().sorted(Comparator.<Map.Entry<UUID, String>>comparingInt(e -> e.getValue().length()).reversed())
            .forEach(entry -> {
                var matcher = mentionPattern(entry.getValue()).matcher(content);
                while (matcher.find()) {
                    boolean overlap = false;
                    for (int i = matcher.start(); i < matcher.end(); i++) if (occupied[i]) { overlap = true; break; }
                    if (!overlap && resolved.size() < 20) {
                        resolved.add(entry.getKey());
                        Arrays.fill(occupied, matcher.start(), matcher.end(), true);
                    }
                }
            });
        return resolved;
    }

    private void afterCommit(Runnable task) {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override public void afterCommit() { task.run(); }
            });
        } else task.run();
    }
}
