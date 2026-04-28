package com.saga.sattolux.module.result.service;

import com.saga.sattolux.module.result.dto.ResultWeekResponse;
import com.saga.sattolux.module.result.dto.ResultManualTestResponse;
import com.saga.sattolux.module.result.dto.ResultManualTestPrepareResponse;

public interface ResultService {
    int collectLatestResults();
    ResultWeekResponse getWeekResult(Long userSeq, Integer year, Integer month, Integer weekOfMonth);
    ResultManualTestPrepareResponse prepareLatestResultManualTestData(Long userSeq);
    ResultManualTestResponse runLatestResultManualTest();
}
