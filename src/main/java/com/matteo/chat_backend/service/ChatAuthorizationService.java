package com.matteo.chat_backend.service;

import com.matteo.chat_backend.entity.Channel;
import com.matteo.chat_backend.entity.DirectMessage;
import com.matteo.chat_backend.entity.User;
import com.matteo.chat_backend.repository.ChannelRepository;
import com.matteo.chat_backend.repository.DirectMessageRepository;
import com.matteo.chat_backend.repository.ServerMemberRepository;
import com.matteo.chat_backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ChatAuthorizationService {

    private final UserRepository userRepository;
    private final ChannelRepository channelRepository;
    private final DirectMessageRepository directMessageRepository;
    private final ServerMemberRepository serverMemberRepository;

    public User requireUser(String email) {
        return userRepository.findByEmail(email)
                .orElseThrow(() -> new AccessDeniedException("Usuario no autorizado"));
    }

    public Channel requireChannelAccess(UUID channelId, String email) {
        User user = requireUser(email);
        Channel channel = channelRepository.findActiveById(channelId)
                .orElseThrow(() -> new AccessDeniedException("Canal no disponible"));
        if (!serverMemberRepository.existsByServerAndUser(channel.getServer(), user)) {
            throw new AccessDeniedException("No tienes acceso a este canal");
        }
        return channel;
    }

    public DirectMessage requireDirectMessageAccess(UUID dmId, String email) {
        User user = requireUser(email);
        DirectMessage directMessage = directMessageRepository.findActiveById(dmId)
                .orElseThrow(() -> new AccessDeniedException("Conversacion no disponible"));
        boolean participant = directMessage.getUser1().getId().equals(user.getId())
                || directMessage.getUser2().getId().equals(user.getId());
        if (!participant) {
            throw new AccessDeniedException("No tienes acceso a esta conversacion");
        }
        return directMessage;
    }

    public void requireSubscriptionAccess(String destination, String email) {
        if (destination == null) {
            throw new AccessDeniedException("Destino no valido");
        }
        if (destination.equals("/topic/presence") || destination.equals("/topic/profile")
                || destination.equals("/user/queue/messages") || destination.equals("/user/queue/friends")
                || destination.equals("/user/queue/server-events")) {
            return;
        }
        UUID channelId = destinationId(destination, "/topic/channel.", "/topic/typing.channel.");
        if (channelId != null) {
            requireChannelAccess(channelId, email);
            return;
        }
        UUID dmId = destinationId(destination, "/topic/dm.", "/topic/typing.dm.");
        if (dmId != null) {
            requireDirectMessageAccess(dmId, email);
            return;
        }
        UUID friendUserId = destinationId(destination, "/topic/friends.");
        if (friendUserId != null && !requireUser(email).getId().equals(friendUserId)) {
            throw new AccessDeniedException("No tienes acceso a estos eventos");
        }
        if (friendUserId != null) {
            return;
        }
        throw new AccessDeniedException("Destino de suscripcion no permitido");
    }

    private UUID destinationId(String destination, String... prefixes) {
        if (destination == null) {
            return null;
        }
        for (String prefix : prefixes) {
            if (destination.startsWith(prefix)) {
                try {
                    return UUID.fromString(destination.substring(prefix.length()));
                } catch (IllegalArgumentException exception) {
                    throw new AccessDeniedException("Destino no valido");
                }
            }
        }
        return null;
    }
}
