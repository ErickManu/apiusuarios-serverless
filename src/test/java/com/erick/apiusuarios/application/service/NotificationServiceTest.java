package com.erick.apiusuarios.application.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.services.sns.SnsClient;
import software.amazon.awssdk.services.sns.model.NotFoundException;
import software.amazon.awssdk.services.sns.model.PublishRequest;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class NotificationServiceTest {
    private final SnsClient sns = mock(SnsClient.class);
    private final String topic = "arn:aws:sns:us-east-2:000000000000:offline-notifications";

    @Test
    void publishesExpectedJsonIncludingUnicodeAndEscaping() throws Exception {
        new NotificationService(sns, topic).enviar("test@example.invalid", "Aviso ñ", "Texto \"citado\"\nSegunda línea");
        var request = ArgumentCaptor.forClass(PublishRequest.class);
        verify(sns).publish(request.capture());
        assertThat(request.getValue().topicArn()).isEqualTo(topic);
        var json = new ObjectMapper().readTree(request.getValue().message());
        assertThat(json.size()).isEqualTo(3);
        assertThat(json.path("email").asText()).isEqualTo("test@example.invalid");
        assertThat(json.path("subject").asText()).isEqualTo("Aviso ñ");
        assertThat(json.path("message").asText()).isEqualTo("Texto \"citado\"\nSegunda línea");
    }

    @Test
    void missingConfigurationDoesNotTouchAws() {
        assertThatThrownBy(() -> new NotificationService(sns, "").enviar("a@b.invalid", "Aviso", "Hola"))
                .isInstanceOf(IllegalStateException.class);
        verifyNoInteractions(sns);
    }

    @Test
    void rejectsUtf8OrEscapedJsonOverSnsLimitBeforePublishing() {
        for (String body : new String[]{"ñ".repeat(140000), "\n".repeat(140000), "a".repeat(262144)}) {
            assertThatThrownBy(() -> new NotificationService(sns, topic).enviar("a@b.invalid", "Aviso", body))
                    .isInstanceOf(IllegalArgumentException.class);
        }
        verifyNoInteractions(sns);
    }

    @Test
    void doesNotSilentlyAcceptMissingTopic() {
        when(sns.publish(any(PublishRequest.class))).thenThrow(NotFoundException.builder().message("missing").build());
        assertThatThrownBy(() -> new NotificationService(sns, topic).enviar("a@b.invalid", "Aviso", "Hola"))
                .isInstanceOf(NotFoundException.class);
    }
}
