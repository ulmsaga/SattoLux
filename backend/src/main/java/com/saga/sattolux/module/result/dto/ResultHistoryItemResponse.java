package com.saga.sattolux.module.result.dto;

import java.util.List;

public record ResultHistoryItemResponse(
        int targetYear,
        int targetMonth,
        int targetWeekOfMonth,
        boolean hasMatch,
        Integer topRank,
        List<RankCount> rankSummary
) {
    public record RankCount(int rank, int count) {}
}
