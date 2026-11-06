package com.borrowbuddy.dto.notification;

import com.borrowbuddy.entity.enums.NotificationType;

import java.time.Instant;

public record NotificationDto(
        Long id,
        String message,
        NotificationType type,
        boolean read,
        Long referenceId,
        String link,
        Instant createdAt
) {
}
