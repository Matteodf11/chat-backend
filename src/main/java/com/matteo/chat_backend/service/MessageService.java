package com.matteo.chat_backend.service;

import com.matteo.chat_backend.dto.MessageRequest;
import com.matteo.chat_backend.dto.MessageResponse;
import com.matteo.chat_backend.dto.MessagePageResponse;
import com.matteo.chat_backend.dto.TypingRequest;
import com.matteo.chat_backend.entity.Channel;
import com.matteo.chat_backend.entity.DirectMessage;
import com.matteo.chat_backend.entity.Message;
import com.matteo.chat_backend.entity.User;
import com.matteo.chat_backend.repository.MessageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.Duration;
import java.util.List;
import java.util.UUID;
import java.util.ArrayList;

@Service
@RequiredArgsConstructor
public class MessageService {

    private final MessageRepository messageRepository;
    private final ChatAuthorizationService authorizationService;
    private final MessageCryptoService cryptoService;
    private final RequestRateLimiter rateLimiter;

    @Transactional
    public MessageResponse saveMessage(MessageRequest request, String senderEmail) {
        validateDestination(request.getChannelId(), request.getDmId());
        rateLimiter.requireCapacity("message:" + senderEmail, 30, Duration.ofSeconds(10), "Estas enviando mensajes demasiado rapido");
        User sender = authorizationService.requireUser(senderEmail);

        Message message = new Message();
        message.setSender(sender);
        message.setContent(cryptoService.encrypt(request.getContent()));
        message.setEdited(false);
        message.setCreatedAt(LocalDateTime.now());

        if (request.getChannelId() != null) {
            Channel channel = authorizationService.requireChannelAccess(request.getChannelId(), senderEmail);
            message.setChannel(channel);
        } else {
            DirectMessage directMessage = authorizationService.requireDirectMessageAccess(request.getDmId(), senderEmail);
            message.setDirectMessage(directMessage);
        }

        return toResponse(messageRepository.save(message));
    }

    public MessagePageResponse getChannelMessages(UUID channelId, LocalDateTime before, int limit, String requesterEmail) {
        authorizationService.requireChannelAccess(channelId, requesterEmail);
        return toPage(messageRepository.findByChannelIdAndCreatedAtBeforeOrderByCreatedAtDesc(
                channelId, cursor(before), PageRequest.of(0, normalizedLimit(limit) + 1)), normalizedLimit(limit));
    }

    public MessagePageResponse getDmMessages(UUID dmId, LocalDateTime before, int limit, String requesterEmail) {
        authorizationService.requireDirectMessageAccess(dmId, requesterEmail);
        return toPage(messageRepository.findByDirectMessageIdAndCreatedAtBeforeOrderByCreatedAtDesc(
                dmId, cursor(before), PageRequest.of(0, normalizedLimit(limit) + 1)), normalizedLimit(limit));
    }

    @Transactional
    public MessageResponse editMessage(UUID messageId, String content, String requesterEmail) {
        Message message = requireOwnedMessage(messageId, requesterEmail);
        if (content == null || content.isBlank() || content.length() > 4000) {
            throw new IllegalArgumentException("El mensaje debe contener entre 1 y 4000 caracteres");
        }
        message.setContent(cryptoService.encrypt(content.trim()));
        message.setEdited(true);
        return toResponse(messageRepository.save(message));
    }

    @Transactional
    public MessageResponse deleteMessage(UUID messageId, String requesterEmail) {
        Message message = requireOwnedMessage(messageId, requesterEmail);
        MessageResponse response = toResponse(message);
        response.setContent("");
        response.setDeleted(true);
        messageRepository.delete(message);
        return response;
    }

    public DirectMessage getDirectMessage(UUID dmId, String requesterEmail) {
        return authorizationService.requireDirectMessageAccess(dmId, requesterEmail);
    }

    public User authorizeTyping(TypingRequest request, String email) {
        validateDestination(request.getChannelId(), request.getDmId());
        rateLimiter.requireCapacity("typing:" + email, 30, Duration.ofSeconds(5), "Demasiados eventos de escritura");
        if (request.getChannelId() != null) {
            authorizationService.requireChannelAccess(request.getChannelId(), email);
        } else {
            authorizationService.requireDirectMessageAccess(request.getDmId(), email);
        }
        return authorizationService.requireUser(email);
    }

    private MessageResponse toResponse(Message message) {
        return new MessageResponse(
                message.getId(),
                cryptoService.decrypt(message.getContent()),
                message.getSender().getId(),
                message.getSender().getUsername(),
                message.getSender().getAvatarUrl(),
                message.getChannel() != null ? message.getChannel().getId() : null,
                message.getDirectMessage() != null ? message.getDirectMessage().getId() : null,
                message.getEdited(),
                false,
                message.getCreatedAt()
        );
    }

    private Message requireOwnedMessage(UUID messageId, String requesterEmail) {
        Message message = messageRepository.findById(messageId)
                .orElseThrow(() -> new IllegalArgumentException("Mensaje no encontrado"));
        authorizationService.requireUser(requesterEmail);
        if (!message.getSender().getEmail().equalsIgnoreCase(requesterEmail)) {
            throw new org.springframework.security.access.AccessDeniedException("Solo puedes modificar tus propios mensajes");
        }
        if (message.getChannel() != null) {
            authorizationService.requireChannelAccess(message.getChannel().getId(), requesterEmail);
        } else {
            authorizationService.requireDirectMessageAccess(message.getDirectMessage().getId(), requesterEmail);
        }
        return message;
    }

    private MessagePageResponse toPage(List<Message> descending, int limit) {
        boolean hasMore = descending.size() > limit;
        List<Message> page = new ArrayList<>(descending.subList(0, Math.min(limit, descending.size())));
        java.util.Collections.reverse(page);
        return new MessagePageResponse(page.stream().map(this::toResponse).toList(), hasMore);
    }

    private int normalizedLimit(int limit) {
        return Math.max(1, Math.min(limit, 100));
    }

    private LocalDateTime cursor(LocalDateTime before) {
        return before != null ? before : LocalDateTime.of(9999, 12, 31, 23, 59, 59);
    }

    private void validateDestination(UUID channelId, UUID dmId) {
        if ((channelId == null) == (dmId == null)) {
            throw new IllegalArgumentException("El mensaje debe pertenecer exactamente a un canal o una conversacion");
        }
    }
}
