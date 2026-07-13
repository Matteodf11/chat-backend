package com.matteo.chat_backend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.UUID;

@Data
@AllArgsConstructor
public class ServerEventResponse {
    private String type;
    private UUID serverId;
    private String role;
}
