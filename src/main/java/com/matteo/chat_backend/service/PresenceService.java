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
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Service
@RequiredArgsConstructor
public class PresenceService {

    private final UserRepository userRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final Map<String, String> sessionEmails = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> openSessionsByEmail = new ConcurrentHashMap<>();

    public void connect(String sessionId, String email) {
        if (sessionId == null || email == null) return;

        sessionEmails.put(sessionId, email);
        int openSessions = openSessionsByEmail
                .computeIfAbsent(email, ignored -> new AtomicInteger(0))
                .incrementAndGet();

        setOnlineStatus(email, true);
    }

    public void disconnect(String sessionId) {
        String email = sessionEmails.remove(sessionId);
        if (email == null) return;

        AtomicInteger openSessions = openSessionsByEmail.get(email);
        if (openSessions != null && openSessions.decrementAndGet() <= 0) {
            openSessionsByEmail.remove(email);
            setOnlineStatus(email, false);
        }
    }

    public void disconnectAll(String email) {
        if (email == null) return;

        sessionEmails.entrySet().removeIf(entry -> email.equals(entry.getValue()));
        openSessionsByEmail.remove(email);
        setOnlineStatus(email, false);
    }

    public void markOnline(String email) {
        if (email == null) return;

        openSessionsByEmail.computeIfAbsent(email, ignored -> new AtomicInteger(0));
        setOnlineStatus(email, true);
    }

    public boolean isOnline(User user) {
        return user != null && isOnline(user.getEmail());
    }

    public boolean isOnline(String email) {
        AtomicInteger openSessions = openSessionsByEmail.get(email);
        return openSessions != null && openSessions.get() > 0;
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
