
package com.erick.apiusuarios.infrastructure.controller;

public record NotificationRequest(
        String email,
        String subject,
        String message
) {}
