package com.matteo.chat_backend.dto;

import java.util.UUID;

public record ChannelActivityResponse(UUID channelId, UUID serverId, long unreadCount, long mentionCount) {}
