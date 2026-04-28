package com.saga.sattolux.module.makeweeknum.dao;

import java.util.List;
import java.util.Map;

public interface MakeWeekNumDao {
    List<Map<String, Object>> findActiveRules(Long userSeq);
    List<Long> findUserSeqsByRuleDayOfWeek(int dayOfWeek);
    List<Map<String, Object>> findRecentDrawResults(int limit);
    void deleteNumberSetsForRuleScope(Long userSeq, Long ruleId, int targetYear, int targetMonth, int targetWeekOfMonth);
    void saveNumberSet(Map<String, Object> params);
    List<Map<String, Object>> findNumberSetsByScope(Long userSeq, int targetYear, int targetMonth, int targetWeekOfMonth);
}
