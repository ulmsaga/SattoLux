package com.saga.sattolux.module.makeweeknum.dto;

public record MakeWeekNumStatusResponse(
        int targetYear,
        int targetMonth,
        int targetWeekOfMonth,
        String schedulerTime,
        String schedulerZone,
        Integer configuredDayOfWeek,
        boolean hasActiveRules,
        boolean todayIsConfiguredDay,
        boolean scheduleTimeReached,
        boolean hasCurrentWeekNumbers,
        boolean manualGenerationAvailable,
        String manualGenerationReason
) {
}
