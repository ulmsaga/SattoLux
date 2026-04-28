package com.saga.sattolux.module.notification.dto;

import java.time.LocalDateTime;

public record NotificationResponse(
        Long notificationId,
        String typeCode,
        String title,
        String message,
        Integer targetYear,
        Integer targetMonth,
        Integer targetWeekOfMonth,
        Integer drawNo,
        String readYn,
        LocalDateTime createdAt
) {
}
