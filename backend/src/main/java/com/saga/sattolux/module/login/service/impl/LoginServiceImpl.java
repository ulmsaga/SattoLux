package com.saga.sattolux.module.login.service.impl;

import com.saga.sattolux.core.auth.AuthSessionStore;
import com.saga.sattolux.core.auth.JwtUtil;
import com.saga.sattolux.core.auth.OtpUtil;
import com.saga.sattolux.core.auth.RsaUtil;
import com.saga.sattolux.core.auth.SecureHashUtil;
import com.saga.sattolux.core.auth.SseConnectionManager;
import com.saga.sattolux.module.login.dao.LoginDao;
import com.saga.sattolux.module.login.dto.LoginRequest;
import com.saga.sattolux.module.login.dto.LoginResponse;
import com.saga.sattolux.module.login.dto.RsaKeyResponse;
import com.saga.sattolux.module.login.dto.TokenResponse;
import com.saga.sattolux.module.login.dto.UserMeResponse;
import com.saga.sattolux.module.login.service.LoginService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class LoginServiceImpl implements LoginService {

    private static final int MAX_LOGIN_FAILURES = 5;
    private static final int ACCOUNT_LOCK_MINUTES = 15;
    private static final int OTP_EXPIRE_MINUTES = 5;
    private static final int MAX_OTP_ATTEMPTS = 5;
    private static final String INVALID_CREDENTIALS_MESSAGE = "로그인 정보가 올바르지 않습니다.";

    private final RsaUtil rsaUtil;
    private final JwtUtil jwtUtil;
    private final OtpUtil otpUtil;
    private final SecureHashUtil secureHashUtil;
    private final AuthSessionStore authSessionStore;
    private final SseConnectionManager sseConnectionManager;
    private final LoginDao loginDao;
    private final PasswordEncoder passwordEncoder;

    @Override
    public RsaKeyResponse generateRsaKey() throws Exception {
        String sessionId = UUID.randomUUID().toString();
        Map<String, String> keyPair = rsaUtil.generateKeyPair(sessionId);
        return new RsaKeyResponse(keyPair.get("sessionId"), keyPair.get("publicKey"));
    }

    @Override
    public LoginResponse login(LoginRequest request) throws Exception {
        Map<String, Object> user = loginDao.findByUserId(request.userId());
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, INVALID_CREDENTIALS_MESSAGE);
        }

        LocalDateTime now = LocalDateTime.now();
        user = normalizeLockState(user, now);
        validateAccountStatus(user, now);

        String rawPassword;
        try {
            rawPassword = rsaUtil.decrypt(request.sessionId(), request.encryptedPassword());
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, INVALID_CREDENTIALS_MESSAGE);
        }

        if (!passwordEncoder.matches(rawPassword, getString(user, "passwordHash"))) {
            handleFailedLogin(user, now);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, INVALID_CREDENTIALS_MESSAGE);
        }

        Long userSeq = getLong(user, "userSeq");
        boolean otpEnabled = getBoolean(user, "otpEnabled");

        loginDao.resetLoginFailures(userSeq);
        loginDao.updateLastLogin(userSeq, now);

        String authSessionToken = authSessionStore.createSession(userSeq, otpEnabled);
        return new LoginResponse(authSessionToken, otpEnabled);
    }

    @Override
    public void sendOtp(String authSessionToken) {
        AuthSessionStore.AuthSession session = requireSession(authSessionToken);
        if (!session.otpEnabled()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "OTP가 비활성화된 계정입니다.");
        }

        Map<String, Object> user = requireUser(session.userSeq());
        validateAccountStatus(user, LocalDateTime.now());

        String code = otpUtil.generateCode();
        String codeHash = secureHashUtil.sha256(code);
        LocalDateTime expiresAt = LocalDateTime.now().plusMinutes(OTP_EXPIRE_MINUTES);

        loginDao.saveOtp(session.userSeq(), codeHash, expiresAt);
        otpUtil.sendOtp(getString(user, "email"), code);
    }

    @Override
    public TokenResponse verifyOtpAndIssueToken(String authSessionToken, String code, String issuedIp, String userAgent) {
        AuthSessionStore.AuthSession session = requireSession(authSessionToken);
        if (!session.otpEnabled()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "OTP가 비활성화된 계정입니다.");
        }

        LocalDateTime now = LocalDateTime.now();
        Map<String, Object> otp = loginDao.findLatestActiveOtp(session.userSeq(), now);
        if (otp == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "유효한 OTP가 없습니다.");
        }

        Long otpId = getLong(otp, "otpId");
        int attemptCount = getInt(otp, "attemptCount");
        String codeHash = secureHashUtil.sha256(code);
        if (!codeHash.equals(getString(otp, "codeHash"))) {
            loginDao.increaseOtpAttempt(otpId);
            if (attemptCount + 1 >= MAX_OTP_ATTEMPTS) {
                loginDao.markOtpUsed(otpId, now);
            }
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "OTP가 올바르지 않습니다.");
        }

        loginDao.markOtpUsed(otpId, now);
        TokenResponse response = buildTokenResponse(session.userSeq(), issuedIp, userAgent);
        authSessionStore.consumeSession(authSessionToken);
        return response;
    }

    @Override
    public TokenResponse issueToken(String authSessionToken, String issuedIp, String userAgent) {
        AuthSessionStore.AuthSession session = requireSession(authSessionToken);
        if (session.otpEnabled()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "OTP 검증이 필요한 계정입니다.");
        }

        TokenResponse response = buildTokenResponse(session.userSeq(), issuedIp, userAgent);
        authSessionStore.consumeSession(authSessionToken);
        return response;
    }

    @Override
    public TokenResponse refresh(String refreshToken, String issuedIp, String userAgent) {
        String refreshTokenHash = secureHashUtil.sha256(refreshToken);
        Map<String, Object> stored = loginDao.findRefreshToken(refreshTokenHash);
        if (stored == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "유효하지 않은 Refresh Token입니다.");
        }

        LocalDateTime now = LocalDateTime.now();
        LocalDateTime expiresAt = (LocalDateTime) stored.get("expiresAt");
        if (expiresAt.isBefore(now)) {
            loginDao.revokeRefreshToken(refreshTokenHash, now);
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Refresh Token이 만료되었습니다.");
        }

        Long userSeq = getLong(stored, "userSeq");
        TokenResponse response = buildTokenResponse(userSeq, issuedIp, userAgent);
        loginDao.revokeRefreshToken(refreshTokenHash, now);
        return response;
    }

    @Override
    public void logout(String refreshToken) {
        String refreshTokenHash = secureHashUtil.sha256(refreshToken);
        Map<String, Object> stored = loginDao.findRefreshToken(refreshTokenHash);
        loginDao.revokeRefreshToken(refreshTokenHash, LocalDateTime.now());
        if (stored != null) {
            sseConnectionManager.disconnectAll(getLong(stored, "userSeq"));
        }
    }

    @Override
    public UserMeResponse getCurrentUser(Long userSeq) {
        Map<String, Object> user = requireUser(userSeq);
        return new UserMeResponse(
                getLong(user, "userSeq"),
                getString(user, "userId"),
                getString(user, "roleCode")
        );
    }

    @Override
    public SseEmitter connectSse(Long userSeq, String accessToken) {
        if (!jwtUtil.isValid(accessToken)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "유효하지 않은 Access Token입니다.");
        }

        String tokenType = String.valueOf(jwtUtil.parse(accessToken).get("type"));
        if (!"access".equals(tokenType)) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Access Token만 SSE 연결에 사용할 수 있습니다.");
        }

        Long tokenUserSeq = jwtUtil.getUserSeq(accessToken);
        if (!tokenUserSeq.equals(userSeq)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "다른 사용자 토큰으로는 SSE 연결할 수 없습니다.");
        }

        Map<String, Object> user = requireUser(userSeq);
        validateAccountStatus(user, LocalDateTime.now());
        return sseConnectionManager.connect(userSeq, getString(user, "userId"));
    }

    private TokenResponse buildTokenResponse(Long userSeq, String issuedIp, String userAgent) {
        Map<String, Object> user = requireUser(userSeq);
        validateAccountStatus(user, LocalDateTime.now());

        String userId = getString(user, "userId");
        String roleCode = getString(user, "roleCode");
        if (roleCode == null || roleCode.isBlank()) {
            roleCode = "USER";
        }
        String accessToken = jwtUtil.generateAccessToken(userSeq, userId, roleCode);
        String refreshToken = jwtUtil.generateRefreshToken(userSeq);

        LocalDateTime expiresAt = LocalDateTime.now()
                .plusSeconds(jwtUtil.getRefreshTokenValidityMs() / 1000);
        loginDao.saveRefreshToken(
                userSeq,
                secureHashUtil.sha256(refreshToken),
                expiresAt,
                issuedIp,
                userAgent
        );

        return new TokenResponse(accessToken, refreshToken);
    }

    private Map<String, Object> requireUser(Long userSeq) {
        Map<String, Object> user = loginDao.findByUserSeq(userSeq);
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "사용자 정보를 찾을 수 없습니다.");
        }
        return normalizeLockState(user, LocalDateTime.now());
    }

    private AuthSessionStore.AuthSession requireSession(String authSessionToken) {
        AuthSessionStore.AuthSession session = authSessionStore.getValidSession(authSessionToken);
        if (session == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "인증 세션이 만료되었거나 유효하지 않습니다.");
        }
        return session;
    }

    private Map<String, Object> normalizeLockState(Map<String, Object> user, LocalDateTime now) {
        String accountStatus = getString(user, "accountStatus");
        LocalDateTime lockedUntil = (LocalDateTime) user.get("lockedUntil");
        if ("LOCKED".equals(accountStatus) && lockedUntil != null && !lockedUntil.isAfter(now)) {
            Long userSeq = getLong(user, "userSeq");
            loginDao.unlockUser(userSeq);
            user.put("accountStatus", "ACTIVE");
            user.put("lockedUntil", null);
            user.put("failedLoginCount", 0);
        }
        return user;
    }

    private void validateAccountStatus(Map<String, Object> user, LocalDateTime now) {
        String accountStatus = getString(user, "accountStatus");
        if ("DISABLED".equals(accountStatus)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "비활성화된 계정입니다.");
        }

        LocalDateTime lockedUntil = (LocalDateTime) user.get("lockedUntil");
        if ("LOCKED".equals(accountStatus) && lockedUntil != null && lockedUntil.isAfter(now)) {
            throw new ResponseStatusException(HttpStatus.LOCKED, "계정이 잠겨 있습니다. 잠시 후 다시 시도하세요.");
        }
    }

    private void handleFailedLogin(Map<String, Object> user, LocalDateTime now) {
        Long userSeq = getLong(user, "userSeq");
        int failedCount = getInt(user, "failedLoginCount");

        if (failedCount + 1 >= MAX_LOGIN_FAILURES) {
            loginDao.lockUser(userSeq, now.plusMinutes(ACCOUNT_LOCK_MINUTES));
            return;
        }

        loginDao.increaseFailedLoginCount(userSeq);
    }

    private String getString(Map<String, Object> row, String key) {
        Object value = row.get(key);
        if (value == null) {
            return null;
        }
        return value.toString();
    }

    private Long getLong(Map<String, Object> row, String key) {
        Object value = row.get(key);
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.valueOf(String.valueOf(value));
    }

    private int getInt(Map<String, Object> row, String key) {
        Object value = row.get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        return Integer.parseInt(String.valueOf(value));
    }

    private boolean getBoolean(Map<String, Object> row, String key) {
        Object value = row.get(key);
        if (value instanceof Boolean bool) {
            return bool;
        }
        if (value instanceof Number number) {
            return number.intValue() == 1;
        }
        return Boolean.parseBoolean(String.valueOf(value));
    }
}
