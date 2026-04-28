package com.saga.sattolux.module.notification.dao.impl;

import com.saga.sattolux.module.notification.dao.NotificationDao;
import lombok.RequiredArgsConstructor;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Repository
@RequiredArgsConstructor
public class NotificationDaoImpl implements NotificationDao {

    private static final String NS = "notification.";

    private final SqlSessionTemplate sql;

    @Override
    public List<Map<String, Object>> findRecentNotifications(Long userSeq, int limit) {
        return sql.selectList(NS + "findRecentNotifications", Map.of(
                "userSeq", userSeq,
                "limit", limit
        ));
    }

    @Override
    public int countUnreadNotifications(Long userSeq) {
        Integer count = sql.selectOne(NS + "countUnreadNotifications", userSeq);
        return count == null ? 0 : count;
    }

    @Override
    public Map<String, Object> findResultReadyNotification(Long userSeq, int targetYear, int targetMonth, int targetWeekOfMonth) {
        return sql.selectOne(NS + "findResultReadyNotification", Map.of(
                "userSeq", userSeq,
                "targetYear", targetYear,
                "targetMonth", targetMonth,
                "targetWeekOfMonth", targetWeekOfMonth
        ));
    }

    @Override
    public void insertNotification(Map<String, Object> params) {
        sql.insert(NS + "insertNotification", params);
    }

    @Override
    public void markNotificationRead(Long userSeq, Long notificationId, LocalDateTime readAt) {
        sql.update(NS + "markNotificationRead", Map.of(
                "userSeq", userSeq,
                "notificationId", notificationId,
                "readAt", readAt
        ));
    }
}
