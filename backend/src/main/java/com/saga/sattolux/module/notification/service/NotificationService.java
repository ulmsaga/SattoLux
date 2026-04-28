package com.saga.sattolux.module.notification.service;

import com.saga.sattolux.module.notification.dto.NotificationSummaryResponse;
import com.saga.sattolux.module.notification.dto.NotificationReplayResponse;

public interface NotificationService {
    NotificationSummaryResponse getNotifications(Long userSeq);
    void markRead(Long userSeq, Long notificationId);
    NotificationReplayResponse replayResultReadyNotification(Long userSeq, int targetYear, int targetMonth, int targetWeekOfMonth);
}
