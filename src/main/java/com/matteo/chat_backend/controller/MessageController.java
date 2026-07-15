package com.matteo.chat_backend.controller;

import com.matteo.chat_backend.dto.MessagePageResponse;
import com.matteo.chat_backend.dto.MessageRequest;
import com.matteo.chat_backend.dto.MessageResponse;
import com.matteo.chat_backend.service.MessageService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.format.annotation.DateTimeFormat;

import java.security.Principal;
import java.time.LocalDateTime;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/messages")
@RequiredArgsConstructor
public class MessageController {

    private final MessageService messageService;
    private final SimpMessagingTemplate messagingTemplate;

    @GetMapping("/channel/{channelId}")
    public ResponseEntity<MessagePageResponse> getChannelMessages(
            @PathVariable UUID channelId,
            @RequestParam(required = false) LocalDateTime before,
            @RequestParam(defaultValue = "50") int limit,
            Principal principal) {
        return ResponseEntity.ok(messageService.getChannelMessages(channelId, before, limit, principal.getName()));
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

    @PostMapping(value = "/channel/{channelId}/attachment", consumes = "multipart/form-data")
    public ResponseEntity<MessageResponse> sendChannelAttachment(
            @PathVariable UUID channelId,
            @RequestPart("file") MultipartFile file,
            @RequestParam(defaultValue = "") String content,
            @RequestParam(required = false) UUID replyToId,
            Principal principal) {
        MessageRequest request = attachmentRequest(content, replyToId, channelId, null);
        MessageResponse response = messageService.saveAttachmentMessage(request, file, principal.getName());
        messagingTemplate.convertAndSend("/topic/channel." + channelId, response);
        return ResponseEntity.ok(response);
    }

    @GetMapping("/dm/{dmId}")
    public ResponseEntity<MessagePageResponse> getDmMessages(
            @PathVariable UUID dmId,
            @RequestParam(required = false) LocalDateTime before,
            @RequestParam(defaultValue = "50") int limit,
            Principal principal) {
        return ResponseEntity.ok(messageService.getDmMessages(dmId, before, limit, principal.getName()));
    }

    @GetMapping("/channel/{channelId}/search")
    public ResponseEntity<List<MessageResponse>> searchChannelMessages(
            @PathVariable UUID channelId,
            @RequestParam(defaultValue = "") String q,
            @RequestParam(defaultValue = "") String sender,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "false") boolean attachmentsOnly,
            @RequestParam(defaultValue = "50") int limit,
            Principal principal) {
        return ResponseEntity.ok(messageService.searchChannelMessages(
                channelId, q, sender, startOfDay(from), endOfDay(to), attachmentsOnly, limit, principal.getName()));
    }

    @GetMapping("/dm/{dmId}/search")
    public ResponseEntity<List<MessageResponse>> searchDmMessages(
            @PathVariable UUID dmId,
            @RequestParam(defaultValue = "") String q,
            @RequestParam(defaultValue = "") String sender,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @RequestParam(defaultValue = "false") boolean attachmentsOnly,
            @RequestParam(defaultValue = "50") int limit,
            Principal principal) {
        return ResponseEntity.ok(messageService.searchDmMessages(
                dmId, q, sender, startOfDay(from), endOfDay(to), attachmentsOnly, limit, principal.getName()));
    }

    @GetMapping("/context/{messageId}")
    public ResponseEntity<MessagePageResponse> getMessageContext(
            @PathVariable UUID messageId,
            Principal principal) {
        return ResponseEntity.ok(messageService.getMessageContext(messageId, principal.getName()));
    }

    @PostMapping("/dm/{dmId}")
    public ResponseEntity<MessageResponse> sendDmMessage(
            @PathVariable UUID dmId,
            @Valid @RequestBody MessageRequest request,
            Principal principal) {
        request.setDmId(dmId);
        request.setChannelId(null);
        MessageResponse response = messageService.saveMessage(request, principal.getName());
        publishDmMessage(response, principal.getName());
        return ResponseEntity.ok(response);
    }

    @PostMapping(value = "/dm/{dmId}/attachment", consumes = "multipart/form-data")
    public ResponseEntity<MessageResponse> sendDmAttachment(
            @PathVariable UUID dmId,
            @RequestPart("file") MultipartFile file,
            @RequestParam(defaultValue = "") String content,
            @RequestParam(required = false) UUID replyToId,
            Principal principal) {
        MessageRequest request = attachmentRequest(content, replyToId, null, dmId);
        MessageResponse response = messageService.saveAttachmentMessage(request, file, principal.getName());
        publishDmMessage(response, principal.getName());
        return ResponseEntity.ok(response);
    }

    @PutMapping("/{messageId}")
    public ResponseEntity<MessageResponse> editMessage(
            @PathVariable UUID messageId,
            @Valid @RequestBody MessageRequest request,
            Principal principal) {
        MessageResponse response = messageService.editMessage(messageId, request.getContent(), principal.getName());
        publishMessageChange(response);
        return ResponseEntity.ok(response);
    }

    @DeleteMapping("/{messageId}")
    public ResponseEntity<Void> deleteMessage(@PathVariable UUID messageId, Principal principal) {
        MessageResponse response = messageService.deleteMessage(messageId, principal.getName());
        publishMessageChange(response);
        return ResponseEntity.noContent().build();
    }

    private MessageRequest attachmentRequest(String content, UUID replyToId, UUID channelId, UUID dmId) {
        MessageRequest request = new MessageRequest();
        request.setContent(content);
        request.setReplyToId(replyToId);
        request.setChannelId(channelId);
        request.setDmId(dmId);
        return request;
    }

    private LocalDateTime startOfDay(LocalDate value) {
        return value == null ? null : value.atStartOfDay();
    }

    private LocalDateTime endOfDay(LocalDate value) {
        return value == null ? null : value.atTime(LocalTime.MAX);
    }

    private void publishDmMessage(MessageResponse response, String requesterEmail) {
        UUID dmId = response.getDmId();
        messagingTemplate.convertAndSend("/topic/dm." + dmId, response);
        var dm = messageService.getDirectMessage(dmId, requesterEmail);
        messagingTemplate.convertAndSendToUser(dm.getUser1().getEmail(), "/queue/messages", response);
        messagingTemplate.convertAndSendToUser(dm.getUser2().getEmail(), "/queue/messages", response);
    }

    private void publishMessageChange(MessageResponse response) {
        if (response.getChannelId() != null) {
            messagingTemplate.convertAndSend("/topic/channel." + response.getChannelId(), response);
        } else {
            messagingTemplate.convertAndSend("/topic/dm." + response.getDmId(), response);
        }
    }
}
