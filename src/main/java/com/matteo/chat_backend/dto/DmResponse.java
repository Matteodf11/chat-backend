package com.matteo.chat_backend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@AllArgsConstructor
public class DmResponse {
    private UUID id;
    private UUID otherUserId;
    private String otherUsername;
    private String otherAvatar;
    private Boolean otherIsOnline;
    private LocalDateTime createdAt;
}