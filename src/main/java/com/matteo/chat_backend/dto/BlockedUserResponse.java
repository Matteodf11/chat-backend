package com.matteo.chat_backend.dto;

import java.util.UUID;
public record BlockedUserResponse(UUID id, String username, String avatarUrl) {}
