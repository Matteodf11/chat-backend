package com.matteo.chat_backend.controller;

import com.matteo.chat_backend.dto.MessageResponse;
import com.matteo.chat_backend.service.MessageService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

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
}