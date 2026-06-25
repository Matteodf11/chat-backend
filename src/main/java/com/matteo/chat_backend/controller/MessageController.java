package com.matteo.chat_backend.controller;

import com.matteo.chat_backend.dto.MessageRequest;
import com.matteo.chat_backend.dto.MessageResponse;
import com.matteo.chat_backend.service.MessageService;
import lombok.RequiredArgsConstructor;
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

    @GetMapping("/channel/{channelId}")
    public ResponseEntity<List<MessageResponse>> getChannelMessages(@PathVariable UUID channelId) {
        return ResponseEntity.ok(messageService.getChannelMessages(channelId));
    }

    @PostMapping("/channel/{channelId}")
    public ResponseEntity<MessageResponse> sendMessage(
            @PathVariable UUID channelId,
            @RequestBody MessageRequest request,
            Principal principal) {
        request.setChannelId(channelId);
        return ResponseEntity.ok(messageService.saveMessage(request, principal.getName()));
    }

    @GetMapping("/dm/{dmId}")
    public ResponseEntity<List<MessageResponse>> getDmMessages(@PathVariable UUID dmId) {
        return ResponseEntity.ok(messageService.getDmMessages(dmId));
    }

    @PostMapping("/dm/{dmId}")
    public ResponseEntity<MessageResponse> sendDmMessage(
            @PathVariable UUID dmId,
            @RequestBody MessageRequest request,
            Principal principal) {
        request.setDmId(dmId);
        return ResponseEntity.ok(messageService.saveMessage(request, principal.getName()));
    }
}