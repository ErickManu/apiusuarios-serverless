package com.erick.apiusuarios.infrastructure.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.Lazy;
import software.amazon.awssdk.http.urlconnection.UrlConnectionHttpClient;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sns.SnsClient;

import java.time.Duration;

@Configuration
public class NotificationConfig {
    @Bean(destroyMethod = "close")
    @Lazy
    public SnsClient snsClient(@Value("${app.notifications.region:${AWS_REGION:us-east-2}}") String region) {
        // Defer AWS initialization until an authenticated notification is sent.
        return SnsClient.builder()
                .region(Region.of(region))
                .httpClientBuilder(UrlConnectionHttpClient.builder()
                        .connectionTimeout(Duration.ofSeconds(2)).socketTimeout(Duration.ofSeconds(5)))
                .overrideConfiguration(configuration -> configuration
                        .apiCallAttemptTimeout(Duration.ofSeconds(7)).apiCallTimeout(Duration.ofSeconds(15)))
                .build();
    }
}
