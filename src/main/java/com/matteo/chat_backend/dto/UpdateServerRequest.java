package com.matteo.chat_backend.dto;

import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class UpdateServerRequest {

    @Size(min = 2, max = 100, message = "El nombre debe tener entre 2 y 100 caracteres")
    private String name;

    private String description;
    private String iconUrl;
}
