package com.saga.sattolux.module.result.dao;

import java.util.List;
import java.util.Map;

public interface ResultDao {
    Integer findLatestSavedDrawNo();
    Map<String, Object> findDrawResultByDrawNo(int drawNo);
    void upsertDrawResult(Map<String, Object> params);
    List<Map<String, Object>> findNumberSetsByScope(int targetYear, int targetMonth, int targetWeekOfMonth);
    void updateNumberSetDrawNo(Long setId, Integer drawNo);
    void upsertMatchResult(Map<String, Object> params);
    Map<String, Object> findWeekDrawResultByScope(int targetYear, int targetMonth, int targetWeekOfMonth);
    List<Map<String, Object>> findMatchedSetsByScope(Long userSeq, int targetYear, int targetMonth, int targetWeekOfMonth);
}
