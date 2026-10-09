package com.matteo.chat_backend.dto;

import lombok.Data;
import jakarta.validation.constraints.Size;
import java.util.UUID;

@Data
public class MessageRequest {
    @Size(max = 4000, message = "El mensaje no puede superar 4000 caracteres")
    private String content;
    private UUID channelId;
    private UUID dmId;
    private UUID replyToId;
    private UUID clientMessageId;
}
