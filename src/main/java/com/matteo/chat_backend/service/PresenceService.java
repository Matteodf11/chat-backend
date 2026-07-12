package com.matteo.chat_backend.service;

import com.matteo.chat_backend.dto.PresenceEvent;
import com.matteo.chat_backend.entity.User;
import com.matteo.chat_backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
public class PresenceService {

    private final UserRepository userRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final Map<String, String> sessionEmails = new ConcurrentHashMap<>();
    private final Map<String, Set<String>> openSessionsByEmail = new ConcurrentHashMap<>();

    public void connect(String sessionId, String email) {
        if (sessionId == null || email == null) return;

        String previousEmail = sessionEmails.putIfAbsent(sessionId, email);
        if (previousEmail != null) return;

        Set<String> sessions = openSessionsByEmail.computeIfAbsent(email, ignored -> ConcurrentHashMap.newKeySet());
        boolean wasOffline = sessions.isEmpty();
        sessions.add(sessionId);
        if (wasOffline) setOnlineStatus(email, true);
    }

    public void disconnect(String sessionId) {
        String email = sessionEmails.remove(sessionId);
        if (email == null) return;

        Set<String> sessions = openSessionsByEmail.get(email);
        if (sessions == null) return;

        sessions.remove(sessionId);
        if (sessions.isEmpty()) {
            openSessionsByEmail.remove(email, sessions);
            setOnlineStatus(email, false);
        }
    }

    public void disconnectAll(String email) {
        if (email == null) return;

        sessionEmails.entrySet().removeIf(entry -> email.equals(entry.getValue()));
        openSessionsByEmail.remove(email);
        setOnlineStatus(email, false);
    }

    /**
     * Kept for compatibility with older clients. Presence is established by an
     * authenticated WebSocket session, so an HTTP request cannot make it online.
     */
    public void markOnline(String email) {
        // No-op by design.
    }

    public boolean isOnline(User user) {
        return user != null && isOnline(user.getEmail());
    }

    public boolean isOnline(String email) {
        Set<String> openSessions = openSessionsByEmail.get(email);
        return openSessions != null && !openSessions.isEmpty();
    }

    private void setOnlineStatus(String email, boolean online) {
        try {
            Optional<User> userOpt = userRepository.findByEmail(email);
            userOpt.ifPresent(user -> {
                user.setIsOnline(online);
                if (!online) user.setLastSeen(LocalDateTime.now());
                userRepository.save(user);
                messagingTemplate.convertAndSend(
                        "/topic/presence",
                        new PresenceEvent(user.getId(), user.getUsername(), online)
                );
            });
        } catch (Exception ignored) {}
    }
}
