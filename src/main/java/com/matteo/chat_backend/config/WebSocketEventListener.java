package com.matteo.chat_backend.config;

import com.matteo.chat_backend.dto.PresenceEvent;
import com.matteo.chat_backend.entity.User;
import com.matteo.chat_backend.repository.UserRepository;
import com.matteo.chat_backend.security.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

@Component
@RequiredArgsConstructor
public class WebSocketEventListener {

    private final UserRepository userRepository;
    private final JwtService jwtService;
    private final SimpMessagingTemplate messagingTemplate;
    private final Map<String, String> sessionEmails = new ConcurrentHashMap<>();
    private final Map<String, AtomicInteger> openSessionsByEmail = new ConcurrentHashMap<>();

    @EventListener
    public void handleConnect(SessionConnectEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        String token = extractToken(accessor);
        if (token != null) {
            String email = jwtService.extractUsername(token);
            if (email != null) {
                sessionEmails.put(accessor.getSessionId(), email);
                int openSessions = openSessionsByEmail
                        .computeIfAbsent(email, ignored -> new AtomicInteger(0))
                        .incrementAndGet();
                if (openSessions == 1) {
                    setOnlineStatus(email, true);
                }
            }
        }
    }

    @EventListener
    public void handleDisconnect(SessionDisconnectEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        String email = sessionEmails.remove(accessor.getSessionId());
        if (email != null) {
            AtomicInteger openSessions = openSessionsByEmail.get(email);
            if (openSessions != null && openSessions.decrementAndGet() <= 0) {
                openSessionsByEmail.remove(email);
                setOnlineStatus(email, false);
            }
        }
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

    private String extractToken(StompHeaderAccessor accessor) {
        try {
            String auth = accessor.getFirstNativeHeader("Authorization");
            if (auth != null && auth.startsWith("Bearer ")) {
                return auth.substring(7);
            }
        } catch (Exception ignored) {}
        return null;
    }
}
