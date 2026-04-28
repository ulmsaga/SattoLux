package com.saga.sattolux.module.login.dao.impl;

import com.saga.sattolux.module.login.dao.DevAdminDao;
import lombok.RequiredArgsConstructor;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.stereotype.Repository;

import java.util.Map;

@Repository
@RequiredArgsConstructor
public class DevAdminDaoImpl implements DevAdminDao {

    private static final String NS = "devAdmin.";

    private final SqlSessionTemplate sql;

    @Override
    public void insertUser(Map<String, Object> params) {
        sql.insert(NS + "insertUser", params);
    }

    @Override
    public void updateUser(Map<String, Object> params) {
        sql.update(NS + "updateUser", params);
    }

    @Override
    public void ensureRandomLocalRule(Long userSeq) {
        sql.insert(NS + "ensureRandomLocalRule", userSeq);
    }

    @Override
    public void ensureHotClaudeRule(Long userSeq) {
        sql.insert(NS + "ensureHotClaudeRule", userSeq);
    }
}
