package com.saga.sattolux.module.notification.dto;

import java.time.LocalDateTime;

public record NotificationReplayResponse(
        Long notificationId,
        String typeCode,
        int targetYear,
        int targetMonth,
        int targetWeekOfMonth,
        Integer drawNo,
        LocalDateTime replayedAt
) {
}
