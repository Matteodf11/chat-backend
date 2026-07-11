package com.matteo.chat_backend.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class RegisterRequest {

    @NotBlank(message = "El username es obligatorio")
    @Size(min = 3, max = 50, message = "El username debe tener entre 3 y 50 caracteres")
    private String username;

    @NotBlank(message = "El email es obligatorio")
    @Email(message = "El email no tiene formato válido")
    @Size(max = 254, message = "El email es demasiado largo")
    private String email;

    @NotBlank(message = "La contraseña es obligatoria")
    @Size(min = 10, max = 72, message = "La contrasena debe tener entre 10 y 72 caracteres")
    private String password;
}
