package com.matteo.chat_backend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class MessagePageResponse {
    private List<MessageResponse> messages;
    private boolean hasMore;
}
