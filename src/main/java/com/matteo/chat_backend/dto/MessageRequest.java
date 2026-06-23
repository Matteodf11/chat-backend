package com.matteo.chat_backend.dto;

import lombok.Data;
import java.util.UUID;

@Data
public class MessageRequest {
    private String content;
    private UUID channelId;
    private UUID dmId;
}