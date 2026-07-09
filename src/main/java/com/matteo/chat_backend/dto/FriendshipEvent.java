package com.matteo.chat_backend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

@Data
@AllArgsConstructor
public class FriendshipEvent {
    private String type;
    private FriendshipResponse friendship;
}
