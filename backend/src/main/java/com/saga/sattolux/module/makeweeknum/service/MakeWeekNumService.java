package com.saga.sattolux.module.makeweeknum.service;

import com.saga.sattolux.module.makeweeknum.dto.GenerationRuleResponse;
import com.saga.sattolux.module.makeweeknum.dto.GenerateNumbersResponse;
import com.saga.sattolux.module.makeweeknum.dto.MakeWeekNumStatusResponse;
import com.saga.sattolux.module.makeweeknum.dto.SattoNumberSetResponse;

import java.time.LocalDate;
import java.util.List;

public interface MakeWeekNumService {
    List<GenerationRuleResponse> getActiveRules(Long userSeq);
    GenerateNumbersResponse generateCurrentWeekNumbers(Long userSeq, boolean force);
    GenerateNumbersResponse generateNumbersForDate(Long userSeq, LocalDate targetDate, boolean force);
    GenerateNumbersResponse generateManualCurrentWeekNumbers(Long userSeq);
    List<SattoNumberSetResponse> getCurrentWeekNumbers(Long userSeq);
    MakeWeekNumStatusResponse getCurrentWeekStatus(Long userSeq);
    int generateScheduledWeekNumbers();
}
