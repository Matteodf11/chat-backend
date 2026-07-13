package com.matteo.chat_backend.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Data;

@Data
public class UpdateServerMemberRoleRequest {
    @NotBlank(message = "Selecciona un rol")
    @Pattern(regexp = "admin|member", message = "Rol no valido")
    private String role;
}
