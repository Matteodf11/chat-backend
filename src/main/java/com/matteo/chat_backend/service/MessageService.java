package com.matteo.chat_backend.service;

import com.matteo.chat_backend.dto.MessageRequest;
import com.matteo.chat_backend.dto.MessageResponse;
import com.matteo.chat_backend.dto.TypingRequest;
import com.matteo.chat_backend.entity.Channel;
import com.matteo.chat_backend.entity.DirectMessage;
import com.matteo.chat_backend.entity.Message;
import com.matteo.chat_backend.entity.User;
import com.matteo.chat_backend.repository.MessageRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.time.Duration;
import java.util.List;
import java.util.UUID;

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

    public List<MessageResponse> getChannelMessages(UUID channelId, String requesterEmail) {
        authorizationService.requireChannelAccess(channelId, requesterEmail);
        return messageRepository.findByChannelIdOrderByCreatedAtAsc(channelId)
                .stream().map(this::toResponse).toList();
    }

    public List<MessageResponse> getDmMessages(UUID dmId, String requesterEmail) {
        authorizationService.requireDirectMessageAccess(dmId, requesterEmail);
        return messageRepository.findByDirectMessageIdOrderByCreatedAtAsc(dmId, Pageable.unpaged())
                .stream().map(this::toResponse).toList();
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
                message.getCreatedAt()
        );
    }

    private void validateDestination(UUID channelId, UUID dmId) {
        if ((channelId == null) == (dmId == null)) {
            throw new IllegalArgumentException("El mensaje debe pertenecer exactamente a un canal o una conversacion");
        }
    }
}
