package com.saga.sattolux.module.result.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record ResultManualTestResponse(
        int drawNo,
        LocalDate drawDate,
        int targetYear,
        int targetMonth,
        int targetWeekOfMonth,
        int comparedSetCount,
        boolean newlyCollected,
        LocalDateTime testedAt
) {
}
