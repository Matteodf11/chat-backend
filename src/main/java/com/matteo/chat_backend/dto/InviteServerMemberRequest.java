package com.matteo.chat_backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class InviteServerMemberRequest {

    @NotBlank(message = "El usuario o email es obligatorio")
    @Size(min = 3, max = 254)
    private String username;
}
