package com.matteo.chat_backend.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateProfileRequest {

    @Size(min = 3, max = 50)
    private String username;

    private String avatarUrl;

    @Size(max = 100)
    private String status;
}