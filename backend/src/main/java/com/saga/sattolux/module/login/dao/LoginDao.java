package com.saga.sattolux.module.login.dao;

import java.time.LocalDateTime;
import java.util.Map;

public interface LoginDao {
    Map<String, Object> findByUserId(String userId);
    Map<String, Object> findByUserSeq(Long userSeq);
    void increaseFailedLoginCount(Long userSeq);
    void resetLoginFailures(Long userSeq);
    void lockUser(Long userSeq, LocalDateTime lockedUntil);
    void unlockUser(Long userSeq);
    void updateLastLogin(Long userSeq, LocalDateTime lastLoginAt);
    void saveOtp(Long userSeq, String codeHash, LocalDateTime expiresAt);
    Map<String, Object> findLatestActiveOtp(Long userSeq, LocalDateTime now);
    void increaseOtpAttempt(Long otpId);
    void markOtpUsed(Long otpId, LocalDateTime usedAt);
    void saveRefreshToken(Long userSeq, String tokenHash, LocalDateTime expiresAt, String issuedIp, String userAgent);
    Map<String, Object> findRefreshToken(String tokenHash);
    void revokeRefreshToken(String tokenHash, LocalDateTime revokedAt);
}
