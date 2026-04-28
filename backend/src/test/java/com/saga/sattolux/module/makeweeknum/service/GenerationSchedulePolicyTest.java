package com.saga.sattolux.module.makeweeknum.service;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GenerationSchedulePolicyTest {

    private final GenerationSchedulePolicy policy = new GenerationSchedulePolicy("09:00", "Asia/Seoul");

    @Test
    void shouldReturnWeekOfMonthForKoreanLocale() {
        int weekOfMonth = policy.weekOfMonth(LocalDate.of(2026, 4, 25));

        assertEquals(4, weekOfMonth);
    }

    @Test
    void shouldTreatConfiguredTimeAsReachedAtSameMinute() {
        ZonedDateTime dateTime = ZonedDateTime.of(2026, 4, 30, 9, 0, 0, 0, ZoneId.of("Asia/Seoul"));

        assertTrue(policy.isScheduleTimeReached(dateTime));
        assertTrue(policy.isExactScheduleMinute(dateTime));
    }

    @Test
    void shouldTreatTimeBeforeScheduleAsNotReached() {
        ZonedDateTime dateTime = ZonedDateTime.of(2026, 4, 30, 8, 59, 0, 0, ZoneId.of("Asia/Seoul"));

        assertFalse(policy.isScheduleTimeReached(dateTime));
        assertFalse(policy.isExactScheduleMinute(dateTime));
    }

    @Test
    void shouldTreatLaterMinuteAsReachedButNotExact() {
        ZonedDateTime dateTime = ZonedDateTime.of(2026, 4, 30, 9, 5, 0, 0, ZoneId.of("Asia/Seoul"));

        assertTrue(policy.isScheduleTimeReached(dateTime));
        assertFalse(policy.isExactScheduleMinute(dateTime));
    }
}
