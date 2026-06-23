package com.matteo.chat_backend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.UUID;

@Data
@AllArgsConstructor
public class ChannelResponse {
    private UUID id;
    private String name;
    private String type;
    private UUID serverId;
    private LocalDateTime createdAt;
}