package com.matteo.chat_backend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@AllArgsConstructor
public class ServerMemberResponse {
    private UUID userId;
    private String username;
    private String avatarUrl;
    private String role;
    private Boolean isOnline;
    private LocalDateTime joinedAt;
}