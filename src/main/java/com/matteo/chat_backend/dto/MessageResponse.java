package com.matteo.chat_backend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@AllArgsConstructor
public class MessageResponse {
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
}
