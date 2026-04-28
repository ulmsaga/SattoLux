package com.saga.sattolux.module.config.service;

import com.saga.sattolux.module.config.dto.GenerationRuleSaveRequest;
import com.saga.sattolux.module.makeweeknum.dto.GenerationRuleResponse;

import java.util.List;

public interface GenerationRuleConfigService {
    List<GenerationRuleResponse> getRules(Long userSeq);
    List<GenerationRuleResponse> saveRules(Long userSeq, GenerationRuleSaveRequest request);
}
