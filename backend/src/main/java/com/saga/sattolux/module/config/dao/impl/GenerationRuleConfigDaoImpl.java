package com.saga.sattolux.module.config.dao.impl;

import com.saga.sattolux.module.config.dao.GenerationRuleConfigDao;
import lombok.RequiredArgsConstructor;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Map;

@Repository
@RequiredArgsConstructor
public class GenerationRuleConfigDaoImpl implements GenerationRuleConfigDao {

    private static final String NS = "generationRuleConfig.";

    private final SqlSessionTemplate sql;

    @Override
    public List<Map<String, Object>> findRulesByUserSeq(Long userSeq) {
        return sql.selectList(NS + "findRulesByUserSeq", userSeq);
    }

    @Override
    public void insertRule(Map<String, Object> params) {
        sql.insert(NS + "insertRule", params);
    }

    @Override
    public void updateRule(Map<String, Object> params) {
        sql.update(NS + "updateRule", params);
    }

    @Override
    public void deleteRule(Long userSeq, Long ruleId) {
        sql.delete(NS + "deleteRule", Map.of(
                "userSeq", userSeq,
                "ruleId", ruleId
        ));
    }
}
