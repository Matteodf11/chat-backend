package com.matteo.chat_backend.controller;

import com.matteo.chat_backend.dto.MessageRequest;
import com.matteo.chat_backend.dto.MessageResponse;
import com.matteo.chat_backend.service.MessageService;
import lombok.RequiredArgsConstructor;
import jakarta.validation.Valid;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.security.Principal;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/messages")
@RequiredArgsConstructor
public class MessageController {

    private final MessageService messageService;
    private final SimpMessagingTemplate messagingTemplate;

    @GetMapping("/channel/{channelId}")
    public ResponseEntity<List<MessageResponse>> getChannelMessages(@PathVariable UUID channelId, Principal principal) {
        return ResponseEntity.ok(messageService.getChannelMessages(channelId, principal.getName()));
    }

    @PostMapping("/channel/{channelId}")
    public ResponseEntity<MessageResponse> sendMessage(
            @PathVariable UUID channelId,
            @Valid @RequestBody MessageRequest request,
        Principal principal) {
        request.setChannelId(channelId);
        request.setDmId(null);
        MessageResponse response = messageService.saveMessage(request, principal.getName());
        messagingTemplate.convertAndSend("/topic/channel." + channelId, response);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/dm/{dmId}")
    public ResponseEntity<List<MessageResponse>> getDmMessages(@PathVariable UUID dmId, Principal principal) {
        return ResponseEntity.ok(messageService.getDmMessages(dmId, principal.getName()));
    }

    @PostMapping("/dm/{dmId}")
    public ResponseEntity<MessageResponse> sendDmMessage(
            @PathVariable UUID dmId,
            @Valid @RequestBody MessageRequest request,
        Principal principal) {
        request.setDmId(dmId);
        request.setChannelId(null);
        MessageResponse response = messageService.saveMessage(request, principal.getName());
        messagingTemplate.convertAndSend("/topic/dm." + dmId, response);
        var dm = messageService.getDirectMessage(dmId, principal.getName());
        messagingTemplate.convertAndSendToUser(dm.getUser1().getEmail(), "/queue/messages", response);
        messagingTemplate.convertAndSendToUser(dm.getUser2().getEmail(), "/queue/messages", response);
        return ResponseEntity.ok(response);
    }
}
