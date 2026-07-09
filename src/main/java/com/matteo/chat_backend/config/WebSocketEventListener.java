package com.matteo.chat_backend.config;

import com.matteo.chat_backend.dto.PresenceEvent;
import com.matteo.chat_backend.security.JwtService;
import com.matteo.chat_backend.service.PresenceService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.event.EventListener;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.web.socket.messaging.SessionConnectEvent;
import org.springframework.web.socket.messaging.SessionDisconnectEvent;

@Component
@RequiredArgsConstructor
public class WebSocketEventListener {

    private final JwtService jwtService;
    private final PresenceService presenceService;

    @EventListener
    public void handleConnect(SessionConnectEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        String token = extractToken(accessor);
        if (token != null) {
            String email = jwtService.extractUsername(token);
            if (email != null) {
                presenceService.connect(accessor.getSessionId(), email);
            }
        }
    }

    @EventListener
    public void handleDisconnect(SessionDisconnectEvent event) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(event.getMessage());
        presenceService.disconnect(accessor.getSessionId());
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
