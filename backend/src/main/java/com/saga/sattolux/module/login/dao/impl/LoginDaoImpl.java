package com.saga.sattolux.module.login.dao.impl;

import com.saga.sattolux.module.login.dao.LoginDao;
import lombok.RequiredArgsConstructor;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

@Repository
@RequiredArgsConstructor
public class LoginDaoImpl implements LoginDao {

    private final SqlSessionTemplate sql;
    private static final String NS = "login.";

    @Override
    public Map<String, Object> findByUserId(String userId) {
        return sql.selectOne(NS + "findByUserId", userId);
    }

    @Override
    public Map<String, Object> findByUserSeq(Long userSeq) {
        return sql.selectOne(NS + "findByUserSeq", userSeq);
    }

    @Override
    public void increaseFailedLoginCount(Long userSeq) {
        sql.update(NS + "increaseFailedLoginCount", userSeq);
    }

    @Override
    public void resetLoginFailures(Long userSeq) {
        sql.update(NS + "resetLoginFailures", userSeq);
    }

    @Override
    public void lockUser(Long userSeq, LocalDateTime lockedUntil) {
        Map<String, Object> params = new HashMap<>();
        params.put("userSeq", userSeq);
        params.put("lockedUntil", lockedUntil);
        sql.update(NS + "lockUser", params);
    }

    @Override
    public void unlockUser(Long userSeq) {
        sql.update(NS + "unlockUser", userSeq);
    }

    @Override
    public void updateLastLogin(Long userSeq, LocalDateTime lastLoginAt) {
        Map<String, Object> params = new HashMap<>();
        params.put("userSeq", userSeq);
        params.put("lastLoginAt", lastLoginAt);
        sql.update(NS + "updateLastLogin", params);
    }

    @Override
    public void saveOtp(Long userSeq, String codeHash, LocalDateTime expiresAt) {
        Map<String, Object> params = new HashMap<>();
        params.put("userSeq", userSeq);
        params.put("codeHash", codeHash);
        params.put("expiresAt", expiresAt);
        sql.insert(NS + "saveOtp", params);
    }

    @Override
    public Map<String, Object> findLatestActiveOtp(Long userSeq, LocalDateTime now) {
        Map<String, Object> params = new HashMap<>();
        params.put("userSeq", userSeq);
        params.put("now", now);
        return sql.selectOne(NS + "findLatestActiveOtp", params);
    }

    @Override
    public void increaseOtpAttempt(Long otpId) {
        sql.update(NS + "increaseOtpAttempt", otpId);
    }

    @Override
    public void markOtpUsed(Long otpId, LocalDateTime usedAt) {
        Map<String, Object> params = new HashMap<>();
        params.put("otpId", otpId);
        params.put("usedAt", usedAt);
        sql.update(NS + "markOtpUsed", params);
    }

    @Override
    public void saveRefreshToken(Long userSeq, String tokenHash, LocalDateTime expiresAt, String issuedIp, String userAgent) {
        Map<String, Object> params = new HashMap<>();
        params.put("userSeq", userSeq);
        params.put("tokenHash", tokenHash);
        params.put("expiresAt", expiresAt);
        params.put("issuedIp", issuedIp);
        params.put("userAgent", userAgent);
        sql.insert(NS + "saveRefreshToken", params);
    }

    @Override
    public Map<String, Object> findRefreshToken(String tokenHash) {
        return sql.selectOne(NS + "findRefreshToken", tokenHash);
    }

    @Override
    public void revokeRefreshToken(String tokenHash, LocalDateTime revokedAt) {
        Map<String, Object> params = new HashMap<>();
        params.put("tokenHash", tokenHash);
        params.put("revokedAt", revokedAt);
        sql.update(NS + "revokeRefreshToken", params);
    }
}
