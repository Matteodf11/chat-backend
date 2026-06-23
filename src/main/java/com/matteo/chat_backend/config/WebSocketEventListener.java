package com.matteo.chat_backend.config;

import com.matteo.chat_backend.entity.User;
import com.matteo.chat_backend.repository.UserRepository;
import com.matteo.chat_backend.security.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectedEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

import java.time.LocalDateTime;
import java.util.Optional;

@Component
@RequiredArgsConstructor
public class WebSocketEventListener {

    private final UserRepository userRepository;
    private final JwtService jwtService;

    @EventListener
    public void handleConnect(SessionConnectedEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        String token = extractToken(accessor);
        if (token != null) {
            setOnlineStatus(token, true);
        }
    }

    @EventListener
    public void handleDisconnect(SessionDisconnectEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        String token = extractToken(accessor);
        if (token != null) {
            setOnlineStatus(token, false);
        }
    }

    private void setOnlineStatus(String token, boolean online) {
        try {
            String email = jwtService.extractUsername(token);
            Optional<User> userOpt = userRepository.findByEmail(email);
            userOpt.ifPresent(user -> {
                user.setIsOnline(online);
                if (!online) user.setLastSeen(LocalDateTime.now());
                userRepository.save(user);
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