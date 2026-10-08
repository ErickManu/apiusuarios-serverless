
package com.erick.apiusuarios.infrastructure.controller;

import com.erick.apiusuarios.application.service.NotificationService;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import software.amazon.awssdk.core.exception.SdkException;
import software.amazon.awssdk.services.sns.model.SnsException;

import java.util.Map;

@RestController
@RequestMapping("/notifications")
public class NotificationController {

    private static final Logger logger = LoggerFactory.getLogger(NotificationController.class);

    private final NotificationService service;

    public NotificationController(NotificationService service) {
        this.service = service;
    }

    @PostMapping("/send")
    public ResponseEntity<?> enviar(@Valid @RequestBody NotificationRequest request) {
        service.enviar(
                request.email(),
                request.subject(),
                request.message()
        );

        return ResponseEntity.accepted().body(
                Map.of("message", "Notificación aceptada para procesamiento")
        );
    }

    // These handlers affect only notifications; existing routes keep their contracts.
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<?> invalidRequest() {
        return ResponseEntity.badRequest().body(Map.of("error", "Campos de notificación inválidos"));
    }

    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<?> messageTooLarge() {
        return ResponseEntity.status(413).body(Map.of("error", "La notificación supera el límite de SNS"));
    }

    @ExceptionHandler({SdkException.class, IllegalStateException.class})
    public ResponseEntity<?> notificationUnavailable(Exception exception) {
        String errorCode = exception instanceof SnsException sns && sns.awsErrorDetails() != null
                ? sns.awsErrorDetails().errorCode() : exception.getClass().getSimpleName();
        logger.error("No se pudo publicar la notificación en SNS. Tipo: {}", errorCode);
        return ResponseEntity.status(503).body(Map.of("error", "Servicio de notificaciones no disponible"));
    }
}
