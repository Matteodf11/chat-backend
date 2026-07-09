package com.matteo.chat_backend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.UUID;

@Data
@AllArgsConstructor
public class PresenceEvent {
    private UUID userId;
    private String username;
    private Boolean online;
}
