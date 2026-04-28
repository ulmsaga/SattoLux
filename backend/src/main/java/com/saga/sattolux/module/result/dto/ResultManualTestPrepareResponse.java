package com.saga.sattolux.module.result.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

public record ResultManualTestPrepareResponse(
        int drawNo,
        LocalDate drawDate,
        int targetYear,
        int targetMonth,
        int targetWeekOfMonth,
        int generatedSetCount,
        LocalDateTime preparedAt
) {
}
