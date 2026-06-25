package com.matteo.chat_backend.service;

import com.matteo.chat_backend.dto.MessageRequest;
import com.matteo.chat_backend.dto.MessageResponse;
import com.matteo.chat_backend.entity.Channel;
import com.matteo.chat_backend.entity.DirectMessage;
import com.matteo.chat_backend.entity.Message;
import com.matteo.chat_backend.entity.User;
import com.matteo.chat_backend.repository.ChannelRepository;
import com.matteo.chat_backend.repository.DirectMessageRepository;
import com.matteo.chat_backend.repository.MessageRepository;
import com.matteo.chat_backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MessageService {

    private final MessageRepository messageRepository;
    private final UserRepository userRepository;
    private final ChannelRepository channelRepository;
    private final DirectMessageRepository directMessageRepository;

    public MessageResponse saveMessage(MessageRequest request, String senderEmail) {
        User sender = userRepository.findByEmail(senderEmail)
                .orElseThrow(() -> new RuntimeException("Usuario no encontrado"));

        Message message = new Message();
        message.setSender(sender);
        message.setContent(request.getContent());
        message.setEdited(false);
        message.setCreatedAt(LocalDateTime.now());

        if (request.getChannelId() != null) {
            Channel channel = channelRepository.findById(request.getChannelId())
                    .orElseThrow(() -> new RuntimeException("Canal no encontrado"));
            message.setChannel(channel);
        } else if (request.getDmId() != null) {
            DirectMessage dm = directMessageRepository.findById(request.getDmId())
                    .orElseThrow(() -> new RuntimeException("Conversación no encontrada"));
            message.setDirectMessage(dm);
        }

        Message saved = messageRepository.save(message);
        return toResponse(saved);
    }

    public List<MessageResponse> getChannelMessages(UUID channelId) {
        return messageRepository.findByChannelIdOrderByCreatedAtAsc(channelId)
                .stream().map(this::toResponse).collect(Collectors.toList());
    }

    private MessageResponse toResponse(Message message) {
        return new MessageResponse(
                message.getId(),
                message.getContent(),
                message.getSender().getUsername(),
                message.getSender().getAvatarUrl(),
                message.getChannel() != null ? message.getChannel().getId() : null,
                message.getDirectMessage() != null ? message.getDirectMessage().getId() : null,
                message.getEdited(),
                message.getCreatedAt()
        );
    }

    public List<MessageResponse> getDmMessages(UUID dmId) {
        return messageRepository.findByDirectMessageIdOrderByCreatedAtAsc(dmId, Pageable.unpaged())
                .stream().map(this::toResponse).collect(Collectors.toList());
    }
}