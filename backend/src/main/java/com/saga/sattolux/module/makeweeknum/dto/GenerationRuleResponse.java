package com.saga.sattolux.module.makeweeknum.dto;

public record GenerationRuleResponse(
        Long ruleId,
        int dayOfWeek,
        String methodCode,
        String generatorCode,
        int setCount,
        Integer analysisDrawCount,
        int sortOrder,
        String useYn
) {
}
