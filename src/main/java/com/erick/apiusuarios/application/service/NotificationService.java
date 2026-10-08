
package com.erick.apiusuarios.application.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.PublishRequest;

import java.nio.charset.StandardCharsets;
import java.util.Map;

@Service
public class NotificationService {

    private static final int SNS_MAX_MESSAGE_BYTES = 262144;

    private final SnsClient snsClient;
    private final ObjectMapper objectMapper;
    private final String topicArn;


    public NotificationService(@Lazy SnsClient snsClient,
                               @Value("${app.notifications.topic-arn:${SNS_TOPIC_ARN:}}") String topicArn) {
        this.objectMapper = new ObjectMapper();
        this.snsClient = snsClient;
        this.topicArn = topicArn;
    }


    public void enviar(String email, String subject, String message) {
        if (topicArn == null || topicArn.isBlank()) {
            throw new IllegalStateException("SNS_TOPIC_ARN no configurado");
        }

        try {
            String mensaje = objectMapper.writeValueAsString(
                    Map.of(
                            "email", email,
                            "subject", subject,
                            "message", message
                    )
            );

            // SNS limits the serialized UTF-8 payload, including JSON escaping.
            if (mensaje.getBytes(StandardCharsets.UTF_8).length > SNS_MAX_MESSAGE_BYTES) {
                throw new IllegalArgumentException("La notificación supera el límite de 256 KiB de SNS");
            }

            snsClient.publish(
                    PublishRequest.builder()
                            .topicArn(topicArn)
                            .message(mensaje)
                            .build()
            );

        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Error preparando el mensaje", e);
        }
    }
}
