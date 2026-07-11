package com.matteo.chat_backend.controller;

import com.matteo.chat_backend.dto.MessageRequest;
import com.matteo.chat_backend.dto.MessageResponse;
import com.matteo.chat_backend.dto.TypingEvent;
import com.matteo.chat_backend.dto.TypingRequest;
import com.matteo.chat_backend.entity.DirectMessage;
import com.matteo.chat_backend.entity.User;
import com.matteo.chat_backend.service.MessageService;
import lombok.RequiredArgsConstructor;
import jakarta.validation.Valid;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.handler.annotation.Payload;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Controller;

import java.security.Principal;

@Controller
@RequiredArgsConstructor
public class ChatController {

    private final SimpMessagingTemplate messagingTemplate;
    private final MessageService messageService;

    @MessageMapping("/chat.sendMessage")
    public void sendMessage(@Valid @Payload MessageRequest request, Principal principal) {
        requirePrincipal(principal);
        MessageResponse response = messageService.saveMessage(request, principal.getName());

        if (request.getChannelId() != null) {
            messagingTemplate.convertAndSend(
                    "/topic/channel." + request.getChannelId(), response
            );
        } else if (request.getDmId() != null) {
            messagingTemplate.convertAndSend(
                    "/topic/dm." + request.getDmId(), response
            );
            publishDmPersonalMessage(request.getDmId(), response, principal.getName());
        }
    }

    @MessageMapping("/chat.typing")
    public void typing(@Payload TypingRequest request, Principal principal) {
        requirePrincipal(principal);
        User sender = messageService.authorizeTyping(request, principal.getName());
        TypingEvent event = new TypingEvent(
                sender.getId(),
                sender.getUsername(),
                request.getChannelId(),
                request.getDmId(),
                Boolean.TRUE.equals(request.getTyping())
        );

        if (request.getChannelId() != null) {
            messagingTemplate.convertAndSend("/topic/typing.channel." + request.getChannelId(), event);
        } else if (request.getDmId() != null) {
            messagingTemplate.convertAndSend("/topic/typing.dm." + request.getDmId(), event);
        }
    }

    private void publishDmPersonalMessage(java.util.UUID dmId, MessageResponse response, String requesterEmail) {
        DirectMessage dm = messageService.getDirectMessage(dmId, requesterEmail);
        messagingTemplate.convertAndSendToUser(dm.getUser1().getEmail(), "/queue/messages", response);
        messagingTemplate.convertAndSendToUser(dm.getUser2().getEmail(), "/queue/messages", response);
    }

    private void requirePrincipal(Principal principal) {
        if (principal == null) {
            throw new AccessDeniedException("Conexion no autenticada");
        }
    }
}
