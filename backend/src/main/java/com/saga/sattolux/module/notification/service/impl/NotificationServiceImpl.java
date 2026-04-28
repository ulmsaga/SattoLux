package com.saga.sattolux.module.notification.service.impl;

import com.saga.sattolux.core.auth.SseConnectionManager;
import com.saga.sattolux.module.notification.dao.NotificationDao;
import com.saga.sattolux.module.notification.dto.NotificationReplayResponse;
import com.saga.sattolux.module.notification.dto.NotificationResponse;
import com.saga.sattolux.module.notification.dto.NotificationSummaryResponse;
import com.saga.sattolux.module.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.http.HttpStatus;

import java.time.LocalDateTime;
import java.util.List;
import java.util.HashMap;
import java.util.Map;

@Service
@RequiredArgsConstructor
public class NotificationServiceImpl implements NotificationService {

    private static final int RECENT_LIMIT = 10;

    private final NotificationDao notificationDao;
    private final SseConnectionManager sseConnectionManager;

    @Override
    public NotificationSummaryResponse getNotifications(Long userSeq) {
        List<NotificationResponse> notifications = notificationDao.findRecentNotifications(userSeq, RECENT_LIMIT)
                .stream()
                .map(this::toResponse)
                .toList();
        return new NotificationSummaryResponse(notificationDao.countUnreadNotifications(userSeq), notifications);
    }

    @Override
    public void markRead(Long userSeq, Long notificationId) {
        notificationDao.markNotificationRead(userSeq, notificationId, LocalDateTime.now());
    }

    @Override
    public NotificationReplayResponse replayResultReadyNotification(Long userSeq, int targetYear, int targetMonth, int targetWeekOfMonth) {
        Map<String, Object> notification = notificationDao.findResultReadyNotification(userSeq, targetYear, targetMonth, targetWeekOfMonth);
        if (notification == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "재전송할 결과 도착 알림이 없습니다.");
        }

        Map<String, Object> payload = new HashMap<>();
        payload.put("type", getString(notification, "typeCode"));
        payload.put("notificationId", getLong(notification, "notificationId"));
        payload.put("title", getString(notification, "title"));
        payload.put("message", getString(notification, "message"));
        payload.put("targetYear", getInt(notification, "targetYear"));
        payload.put("targetMonth", getInt(notification, "targetMonth"));
        payload.put("targetWeekOfMonth", getInt(notification, "targetWeekOfMonth"));
        payload.put("drawNo", notification.get("drawNo") == null ? null : getInt(notification, "drawNo"));
        payload.put("createdAt", toLocalDateTime(notification.get("createdAt")).toString());
        sseConnectionManager.sendToUser(userSeq, "notification", payload);

        return new NotificationReplayResponse(
                getLong(notification, "notificationId"),
                getString(notification, "typeCode"),
                getInt(notification, "targetYear"),
                getInt(notification, "targetMonth"),
                getInt(notification, "targetWeekOfMonth"),
                notification.get("drawNo") == null ? null : getInt(notification, "drawNo"),
                LocalDateTime.now()
        );
    }

    private NotificationResponse toResponse(Map<String, Object> row) {
        return new NotificationResponse(
                getLong(row, "notificationId"),
                getString(row, "typeCode"),
                getString(row, "title"),
                getString(row, "message"),
                row.get("targetYear") == null ? null : getInt(row, "targetYear"),
                row.get("targetMonth") == null ? null : getInt(row, "targetMonth"),
                row.get("targetWeekOfMonth") == null ? null : getInt(row, "targetWeekOfMonth"),
                row.get("drawNo") == null ? null : getInt(row, "drawNo"),
                getString(row, "readYn"),
                toLocalDateTime(row.get("createdAt"))
        );
    }

    private String getString(Map<String, Object> row, String key) {
        Object value = row.get(key);
        return value == null ? null : value.toString();
    }

    private Long getLong(Map<String, Object> row, String key) {
        Object value = row.get(key);
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.valueOf(String.valueOf(value));
    }

    private int getInt(Map<String, Object> row, String key) {
        Object value = row.get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        return Integer.parseInt(String.valueOf(value));
    }

    private LocalDateTime toLocalDateTime(Object value) {
        if (value instanceof LocalDateTime localDateTime) {
            return localDateTime;
        }
        if (value instanceof java.sql.Timestamp timestamp) {
            return timestamp.toLocalDateTime();
        }
        if (value instanceof java.sql.Date date) {
            return date.toLocalDate().atStartOfDay();
        }
        return LocalDateTime.parse(String.valueOf(value).replace(' ', 'T'));
    }
}
