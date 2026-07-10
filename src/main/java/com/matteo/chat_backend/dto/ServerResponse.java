package com.matteo.chat_backend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@AllArgsConstructor
public class ServerResponse {
    private UUID id;
    private String name;
    private String description;
    private String iconUrl;
    private String ownerUsername;
    private String myRole;
    private LocalDateTime createdAt;
}
