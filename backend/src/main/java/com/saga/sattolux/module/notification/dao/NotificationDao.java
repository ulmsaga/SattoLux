package com.saga.sattolux.module.notification.dao;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

public interface NotificationDao {
    List<Map<String, Object>> findRecentNotifications(Long userSeq, int limit);
    int countUnreadNotifications(Long userSeq);
    Map<String, Object> findResultReadyNotification(Long userSeq, int targetYear, int targetMonth, int targetWeekOfMonth);
    void insertNotification(Map<String, Object> params);
    void markNotificationRead(Long userSeq, Long notificationId, LocalDateTime readAt);
}
