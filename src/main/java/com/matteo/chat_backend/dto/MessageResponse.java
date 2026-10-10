package com.matteo.chat_backend.dto;

import lombok.Data;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
public class MessageResponse {
    private java.util.List<UUID> mentionedUserIds = java.util.List.of();
    private UUID id;
    private String content;
    private UUID senderId;
    private String senderUsername;
    private String senderAvatar;
    private UUID channelId;
    private UUID dmId;
    private Boolean edited;
    private Boolean deleted;
    private UUID replyToId;
    private String replyToSenderUsername;
    private String replyToContent;
    private String attachmentName;
    private String attachmentContentType;
    private Long attachmentSize;
    private String attachmentUrl;
    private LocalDateTime createdAt;
    private UUID clientMessageId;
    private LocalDateTime readAt;

    public MessageResponse(UUID id, String content, UUID senderId, String senderUsername, String senderAvatar,
            UUID channelId, UUID dmId, Boolean edited, Boolean deleted, UUID replyToId,
            String replyToSenderUsername, String replyToContent, String attachmentName,
            String attachmentContentType, Long attachmentSize, String attachmentUrl, LocalDateTime createdAt,
            UUID clientMessageId, LocalDateTime readAt) {
        this.id = id;
        this.content = content;
        this.senderId = senderId;
        this.senderUsername = senderUsername;
        this.senderAvatar = senderAvatar;
        this.channelId = channelId;
        this.dmId = dmId;
        this.edited = edited;
        this.deleted = deleted;
        this.replyToId = replyToId;
        this.replyToSenderUsername = replyToSenderUsername;
        this.replyToContent = replyToContent;
        this.attachmentName = attachmentName;
        this.attachmentContentType = attachmentContentType;
        this.attachmentSize = attachmentSize;
        this.attachmentUrl = attachmentUrl;
        this.createdAt = createdAt;
        this.clientMessageId = clientMessageId;
        this.readAt = readAt;
    }
}
