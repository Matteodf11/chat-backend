package com.matteo.chat_backend.dto;

import java.util.List;
import java.util.UUID;

public record SharedItemsResponse(List<MessageResponse> messages, boolean hasMore, UUID nextBefore) {}
