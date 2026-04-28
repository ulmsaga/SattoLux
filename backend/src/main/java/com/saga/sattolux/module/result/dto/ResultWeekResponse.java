package com.saga.sattolux.module.result.dto;

import java.time.LocalDate;
import java.util.List;

public record ResultWeekResponse(
        int targetYear,
        int targetMonth,
        int targetWeekOfMonth,
        Integer drawNo,
        LocalDate drawDate,
        List<Integer> winningNumbers,
        Integer bonusNo,
        List<ResultSetItemResponse> items
) {
}
