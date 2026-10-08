
package com.erick.apiusuarios.infrastructure.controller;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

public record NotificationRequest(
        @NotBlank @Email @Size(max = 254)
        @Pattern(regexp = "^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$",
                message = "Correo electrónico inválido") String email,
        @NotBlank @Size(max = 998)
        @Pattern(regexp = "^[^\\r\\n]*$", message = "El asunto debe tener una sola línea") String subject,
        @NotBlank @Size(max = 262144) String message
) {}
