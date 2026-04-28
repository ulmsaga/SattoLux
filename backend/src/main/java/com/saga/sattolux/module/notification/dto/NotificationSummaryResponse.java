package com.saga.sattolux.module.notification.dto;

import java.util.List;

public record NotificationSummaryResponse(
        int unreadCount,
        List<NotificationResponse> notifications
) {
}
