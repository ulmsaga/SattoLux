package com.saga.sattolux.module.makeweeknum.dto;

import java.time.LocalDateTime;
import java.util.List;

public record SattoNumberSetResponse(
        Long setId,
        Long ruleId,
        int targetYear,
        int targetMonth,
        int targetWeekOfMonth,
        Integer drawNo,
        String methodCode,
        String generatorCode,
        List<Integer> numbers,
        LocalDateTime createdAt
) {
}
