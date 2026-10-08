
package com.erick.apiusuarios.infrastructure.controller;

import com.erick.apiusuarios.application.service.NotificationService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/notifications")
public class NotificationController {

    private final NotificationService service;

    public NotificationController(NotificationService service) {
        this.service = service;
    }

    @PostMapping("/send")
    public ResponseEntity<?> enviar(@RequestBody NotificationRequest request) {

        if (request == null ||
                request.email() == null || request.email().isBlank() ||
                request.subject() == null || request.subject().isBlank() ||
                request.message() == null || request.message().isBlank()) {

            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Todos los campos son obligatorios"));
        }

        if (!request.email().matches("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$")) {
            return ResponseEntity.badRequest()
                    .body(Map.of("error", "Correo electrónico inválido"));
        }

        service.enviar(
                request.email(),
                request.subject(),
                request.message()
        );

        return ResponseEntity.accepted().body(
                Map.of("message", "Notificación aceptada para procesamiento")
        );
    }
}
