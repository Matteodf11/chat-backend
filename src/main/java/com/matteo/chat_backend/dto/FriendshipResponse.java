package com.matteo.chat_backend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@AllArgsConstructor
public class FriendshipResponse {
    private UUID id;
    private UUID friendId;
    private String friendUsername;
    private String friendAvatar;
    private Boolean friendIsOnline;
    private String status;
    private Boolean isIncoming;
    private LocalDateTime createdAt;
}