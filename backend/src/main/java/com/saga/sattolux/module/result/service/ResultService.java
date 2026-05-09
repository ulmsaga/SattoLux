package com.saga.sattolux.module.result.service;

import com.saga.sattolux.module.result.dto.ResultHistoryItemResponse;
import com.saga.sattolux.module.result.dto.ResultWeekResponse;
import com.saga.sattolux.module.result.dto.ResultManualTestResponse;
import com.saga.sattolux.module.result.dto.ResultManualTestPrepareResponse;

import java.util.List;

public interface ResultService {
    int collectLatestResults();
    ResultWeekResponse getWeekResult(Long userSeq, Integer year, Integer month, Integer weekOfMonth);
    List<ResultHistoryItemResponse> getResultHistory(Long userSeq);
    ResultWeekResponse getResultHistoryDetail(Long userSeq, int year, int month, int weekOfMonth);
    ResultManualTestPrepareResponse prepareLatestResultManualTestData(Long userSeq);
    ResultManualTestResponse runLatestResultManualTest();
}
