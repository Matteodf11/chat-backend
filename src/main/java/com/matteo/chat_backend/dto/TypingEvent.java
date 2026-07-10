package com.matteo.chat_backend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.UUID;

@Data
@AllArgsConstructor
public class TypingEvent {
    private UUID senderId;
    private String senderUsername;
    private UUID channelId;
    private UUID dmId;
    private Boolean typing;
}
