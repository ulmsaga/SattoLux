package com.saga.sattolux.module.result.dto;

import java.util.List;

public record ResultSetItemResponse(
        Long setId,
        Long ruleId,
        String methodCode,
        String generatorCode,
        List<Integer> numbers,
        Integer matchCount,
        boolean bonusMatch,
        Integer rank
) {
}
