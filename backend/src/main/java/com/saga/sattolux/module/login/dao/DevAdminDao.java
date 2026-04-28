package com.saga.sattolux.module.login.dao;

import java.util.Map;

public interface DevAdminDao {
    void insertUser(Map<String, Object> params);
    void ensureRandomLocalRule(Long userSeq);
    void ensureHotClaudeRule(Long userSeq);
}
