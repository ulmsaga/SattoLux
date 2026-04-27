package com.saga.sattolux.module.makeweeknum.service;

import com.saga.sattolux.module.makeweeknum.dto.GenerationRuleResponse;
import com.saga.sattolux.module.makeweeknum.dto.GenerateNumbersResponse;
import com.saga.sattolux.module.makeweeknum.dto.SattoNumberSetResponse;

import java.util.List;

public interface MakeWeekNumService {
    List<GenerationRuleResponse> getActiveRules(Long userSeq);
    GenerateNumbersResponse generateCurrentWeekNumbers(Long userSeq, boolean force);
    List<SattoNumberSetResponse> getCurrentWeekNumbers(Long userSeq);
}
