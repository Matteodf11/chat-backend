package com.matteo.chat_backend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.UUID;

@Data
@AllArgsConstructor
public class ServerInvitationResponse {
    private UUID id;
    private UUID serverId;
    private String serverName;
    private String serverIconUrl;
    private String inviterUsername;
    private String status;
    private LocalDateTime createdAt;
}
