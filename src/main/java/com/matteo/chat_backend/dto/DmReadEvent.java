package com.matteo.chat_backend.dto;

import java.time.LocalDateTime;
import java.util.UUID;

public record DmReadEvent(String type, UUID dmId, UUID readerId, LocalDateTime readAt) {}
