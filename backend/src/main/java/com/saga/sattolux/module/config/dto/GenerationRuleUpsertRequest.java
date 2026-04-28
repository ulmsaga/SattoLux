package com.saga.sattolux.module.config.dto;

public record GenerationRuleUpsertRequest(
        Long ruleId,
        Integer dayOfWeek,
        String methodCode,
        String generatorCode,
        Integer setCount,
        Integer analysisDrawCount,
        String useYn
) {
}
