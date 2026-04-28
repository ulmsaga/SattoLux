package com.saga.sattolux.module.config.dto;

import java.util.List;

public record GenerationRuleSaveRequest(
        List<GenerationRuleUpsertRequest> rules
) {
}
