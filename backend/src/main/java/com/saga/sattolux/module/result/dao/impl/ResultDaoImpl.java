package com.saga.sattolux.module.result.dao.impl;

import com.saga.sattolux.module.result.dao.ResultDao;
import lombok.RequiredArgsConstructor;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

@Repository
@RequiredArgsConstructor
public class ResultDaoImpl implements ResultDao {

    private static final String NS = "result.";

    private final SqlSessionTemplate sql;

    @Override
    public Integer findLatestSavedDrawNo() {
        return sql.selectOne(NS + "findLatestSavedDrawNo");
    }

    @Override
    public Map<String, Object> findDrawResultByDrawNo(int drawNo) {
        return sql.selectOne(NS + "findDrawResultByDrawNo", drawNo);
    }

    @Override
    public void upsertDrawResult(Map<String, Object> params) {
        sql.insert(NS + "upsertDrawResult", params);
    }

    @Override
    public List<Map<String, Object>> findNumberSetsByScope(int targetYear, int targetMonth, int targetWeekOfMonth) {
        return sql.selectList(NS + "findNumberSetsByScope", Map.of(
                "targetYear", targetYear,
                "targetMonth", targetMonth,
                "targetWeekOfMonth", targetWeekOfMonth
        ));
    }

    @Override
    public void updateNumberSetDrawNo(Long setId, Integer drawNo) {
        sql.update(NS + "updateNumberSetDrawNo", Map.of(
                "setId", setId,
                "drawNo", drawNo
        ));
    }

    @Override
    public void upsertMatchResult(Map<String, Object> params) {
        sql.insert(NS + "upsertMatchResult", params);
    }

    @Override
    public Map<String, Object> findWeekDrawResultByScope(int targetYear, int targetMonth, int targetWeekOfMonth) {
        return sql.selectOne(NS + "findWeekDrawResultByScope", Map.of(
                "targetYear", targetYear,
                "targetMonth", targetMonth,
                "targetWeekOfMonth", targetWeekOfMonth
        ));
    }

    @Override
    public List<Map<String, Object>> findMatchedSetsByScope(Long userSeq, int targetYear, int targetMonth, int targetWeekOfMonth) {
        return sql.selectList(NS + "findMatchedSetsByScope", Map.of(
                "userSeq", userSeq,
                "targetYear", targetYear,
                "targetMonth", targetMonth,
                "targetWeekOfMonth", targetWeekOfMonth
        ));
    }
}
