
package com.erick.apiusuarios.application.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.PublishRequest;

import java.util.Map;

@Service
public class NotificationService {

    private final SnsClient snsClient;
    private final ObjectMapper objectMapper;
    private final String topicArn;


    public NotificationService() {
        this.objectMapper = new ObjectMapper();
        this.snsClient = SnsClient.builder().build();
        this.topicArn = System.getenv("SNS_TOPIC_ARN");
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
