package com.matteo.chat_backend.controller;

import com.matteo.chat_backend.dto.MessageRequest;
import com.matteo.chat_backend.dto.MessageResponse;
import com.matteo.chat_backend.service.MessageService;
import lombok.RequiredArgsConstructor;
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
    public void sendMessage(@Payload MessageRequest request, Principal principal) {
        MessageResponse response = messageService.saveMessage(request, principal.getName());

        if (request.getChannelId() != null) {
            messagingTemplate.convertAndSend(
                    "/topic/channel." + request.getChannelId(), response
            );
        } else if (request.getDmId() != null) {
            messagingTemplate.convertAndSend(
                    "/queue/dm." + request.getDmId(), response
            );
        }
    }
}