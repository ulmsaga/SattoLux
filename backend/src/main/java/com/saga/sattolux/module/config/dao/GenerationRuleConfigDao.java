package com.saga.sattolux.module.config.dao;

import java.util.List;
import java.util.Map;

public interface GenerationRuleConfigDao {
    List<Map<String, Object>> findRulesByUserSeq(Long userSeq);
    void insertRule(Map<String, Object> params);
    void updateRule(Map<String, Object> params);
    void deleteRule(Long userSeq, Long ruleId);
}
