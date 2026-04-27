package com.saga.sattolux.module.makeweeknum.dao.impl;

import com.saga.sattolux.module.makeweeknum.dao.MakeWeekNumDao;
import lombok.RequiredArgsConstructor;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

@Repository
@RequiredArgsConstructor
public class MakeWeekNumDaoImpl implements MakeWeekNumDao {

    private static final String NS = "makeWeekNum.";

    private final SqlSessionTemplate sql;

    @Override
    public List<Map<String, Object>> findActiveRules(Long userSeq) {
        return sql.selectList(NS + "findActiveRules", userSeq);
    }

    @Override
    public List<Map<String, Object>> findRecentDrawResults(int limit) {
        return sql.selectList(NS + "findRecentDrawResults", limit);
    }

    @Override
    public void deleteNumberSetsForRuleScope(Long userSeq, Long ruleId, int targetYear, int targetMonth, int targetWeekOfMonth) {
        sql.delete(NS + "deleteNumberSetsForRuleScope", Map.of(
                "userSeq", userSeq,
                "ruleId", ruleId,
                "targetYear", targetYear,
                "targetMonth", targetMonth,
                "targetWeekOfMonth", targetWeekOfMonth
        ));
    }

    @Override
    public void saveNumberSet(Map<String, Object> params) {
        sql.insert(NS + "saveNumberSet", params);
    }

    @Override
    public List<Map<String, Object>> findNumberSetsByScope(Long userSeq, int targetYear, int targetMonth, int targetWeekOfMonth) {
        return sql.selectList(NS + "findNumberSetsByScope", Map.of(
                "userSeq", userSeq,
                "targetYear", targetYear,
                "targetMonth", targetMonth,
                "targetWeekOfMonth", targetWeekOfMonth
        ));
    }
}
