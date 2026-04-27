package com.saga.sattolux.module.makeweeknum.dto;

public record SkippedRuleResponse(
        Long ruleId,
        String methodCode,
        String generatorCode,
        String reason
) {
}
