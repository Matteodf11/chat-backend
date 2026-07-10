package com.matteo.chat_backend.dto;

import lombok.Data;

import java.util.UUID;

@Data
public class TypingRequest {
    private UUID channelId;
    private UUID dmId;
    private Boolean typing;
}
