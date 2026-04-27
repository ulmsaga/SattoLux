package com.saga.sattolux.module.makeweeknum.dto;

import java.util.List;

public record GenerateNumbersResponse(
        int targetYear,
        int targetMonth,
        int targetWeekOfMonth,
        int generatedCount,
        List<SattoNumberSetResponse> generatedSets,
        List<SkippedRuleResponse> skippedRules
) {
}
