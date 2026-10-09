package com.matteo.chat_backend.dto;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.UUID;

@Data
@AllArgsConstructor
public class ProfileEvent {
    private UUID userId;
    private String username;
    private String avatarUrl;
    private String profileBannerUrl;
    private String status;
    private String profileColor;
    private String bio;
}
