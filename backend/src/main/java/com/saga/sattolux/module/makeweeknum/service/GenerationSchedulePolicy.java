package com.saga.sattolux.module.makeweeknum.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.WeekFields;
import java.util.Locale;

@Component
public class GenerationSchedulePolicy {

    private final LocalTime schedulerTime;
    private final ZoneId zoneId;

    public GenerationSchedulePolicy(@Value("${satto.generation.scheduler.time:09:00}") String schedulerTime,
                                    @Value("${satto.generation.scheduler.zone:Asia/Seoul}") String schedulerZone) {
        this.schedulerTime = LocalTime.parse(schedulerTime);
        this.zoneId = ZoneId.of(schedulerZone);
    }

    public ZonedDateTime now() {
        return ZonedDateTime.now(zoneId);
    }

    public LocalDate today() {
        return now().toLocalDate();
    }

    public LocalDateTime nowLocalDateTime() {
        return now().toLocalDateTime();
    }

    public int weekOfMonth(LocalDate date) {
        return date.get(WeekFields.of(Locale.KOREA).weekOfMonth());
    }

    public boolean isScheduleTimeReached(ZonedDateTime dateTime) {
        return !dateTime.toLocalTime().isBefore(schedulerTime);
    }

    public boolean isExactScheduleMinute(ZonedDateTime dateTime) {
        return dateTime.getHour() == schedulerTime.getHour() && dateTime.getMinute() == schedulerTime.getMinute();
    }

    public LocalTime schedulerTime() {
        return schedulerTime;
    }

    public String schedulerTimeText() {
        return schedulerTime.toString();
    }

    public String schedulerZoneText() {
        return zoneId.getId();
    }
}
