package com.saga.sattolux.module.login.service;

import com.saga.sattolux.module.login.dto.UserMeResponse;
import com.saga.sattolux.module.login.dto.LoginRequest;
import com.saga.sattolux.module.login.dto.LoginResponse;
import com.saga.sattolux.module.login.dto.RsaKeyResponse;
import com.saga.sattolux.module.login.dto.TokenResponse;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

public interface LoginService {
    RsaKeyResponse generateRsaKey() throws Exception;
    LoginResponse login(LoginRequest request) throws Exception;
    void sendOtp(String authSessionToken);
    TokenResponse verifyOtpAndIssueToken(String authSessionToken, String code, String issuedIp, String userAgent);
    TokenResponse issueToken(String authSessionToken, String issuedIp, String userAgent);
    TokenResponse refresh(String refreshToken, String issuedIp, String userAgent);
    void logout(String refreshToken);
    UserMeResponse getCurrentUser(Long userSeq);
    SseEmitter connectSse(Long userSeq, String accessToken);
}
