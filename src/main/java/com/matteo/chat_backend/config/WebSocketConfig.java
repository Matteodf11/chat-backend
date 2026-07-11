package com.matteo.chat_backend.config;

import com.matteo.chat_backend.security.JwtService;
import com.matteo.chat_backend.repository.UserRepository;
import com.matteo.chat_backend.service.ChatAuthorizationService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.MessageDeliveryException;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final ChatAuthorizationService chatAuthorizationService;

    @Override
    public void configureMessageBroker(MessageBrokerRegistry registry) {
        registry.enableSimpleBroker("/topic", "/queue");
        registry.setApplicationDestinationPrefixes("/app");
        registry.setUserDestinationPrefix("/user");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(new ChannelInterceptor() {
            @Override
            public Message<?> preSend(Message<?> message, MessageChannel channel) {
                StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);
                if (StompCommand.CONNECT.equals(accessor.getCommand())) {
                    String authHeader = accessor.getFirstNativeHeader("Authorization");
                    if (authHeader == null || !authHeader.startsWith("Bearer ")) {
                        throw new MessageDeliveryException("Conexion WebSocket no autenticada");
                    }
                    try {
                        String token = authHeader.substring(7);
                        String email = jwtService.extractUsername(token);
                        if (email == null || !jwtService.isTokenValid(token, email) || !userRepository.existsByEmail(email)) {
                            throw new MessageDeliveryException("Token WebSocket no valido");
                        }
                        accessor.setUser(() -> email);
                    } catch (MessageDeliveryException exception) {
                        throw exception;
                    } catch (RuntimeException exception) {
                        throw new MessageDeliveryException(message, exception);
                    }
                }
                if (StompCommand.SUBSCRIBE.equals(accessor.getCommand())) {
                    if (accessor.getUser() == null) {
                        throw new MessageDeliveryException("Suscripcion no autenticada");
                    }
                    try {
                        chatAuthorizationService.requireSubscriptionAccess(accessor.getDestination(), accessor.getUser().getName());
                    } catch (RuntimeException exception) {
                        throw new MessageDeliveryException(message, exception);
                    }
                }
                return message;
            }
        });
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws-native")
                .setAllowedOriginPatterns("*");

        registry.addEndpoint("/ws")
                .setAllowedOriginPatterns("*")
                .withSockJS();
    }
}
