package com.saga.sattolux.module.login.service.impl;

import com.saga.sattolux.module.login.dao.DevAdminDao;
import com.saga.sattolux.module.login.dao.LoginDao;
import com.saga.sattolux.module.login.dto.DevLoginAccountSyncResponse;
import com.saga.sattolux.module.login.dto.DevUserEnsureResponse;
import com.saga.sattolux.module.login.service.DevAdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Profile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.Map;

@Service
@Profile("dev")
@RequiredArgsConstructor
public class DevAdminServiceImpl implements DevAdminService {

    private final LoginDao loginDao;
    private final DevAdminDao devAdminDao;
    private final PasswordEncoder passwordEncoder;

    @Value("${LOGIN_ADMIN}")
    private String adminLoginUser;

    @Value("${LOGIN_ADMIN_PW:}")
    private String adminLoginPassword;

    @Value("${LOGIN_ADMIN_EMAIL:${LOGIN_ADMIN}@sattolux.local}")
    private String adminLoginEmail;

    @Value("${LOGIN_USER}")
    private String generalLoginUser;

    @Value("${LOGIN_PW:}")
    private String generalLoginPassword;

    @Value("${LOGIN_EMAIL:${LOGIN_USER}@sattolux.local}")
    private String generalLoginEmail;

    @Override
    @Transactional
    public DevUserEnsureResponse ensureGeneralUser() {
        SyncedUser syncedUser = ensureUser(generalLoginUser, generalLoginPassword, generalLoginEmail, "USER");

        return new DevUserEnsureResponse(
                getString(syncedUser.user(), "userId"),
                getString(syncedUser.user(), "email"),
                getString(syncedUser.user(), "roleCode"),
                syncedUser.created(),
                true
        );
    }

    @Override
    @Transactional
    public DevLoginAccountSyncResponse syncLoginAccounts() {
        SyncedUser adminUser = ensureUser(adminLoginUser, adminLoginPassword, adminLoginEmail, "ADMIN");
        SyncedUser loginUser = ensureUser(generalLoginUser, generalLoginPassword, generalLoginEmail, "USER");

        return new DevLoginAccountSyncResponse(
                getString(adminUser.user(), "userId"),
                adminUser.created(),
                getString(adminUser.user(), "roleCode"),
                getString(loginUser.user(), "userId"),
                loginUser.created(),
                getString(loginUser.user(), "roleCode"),
                java.time.LocalDateTime.now()
        );
    }

    private String getString(Map<String, Object> row, String key) {
        Object value = row.get(key);
        return value == null ? null : value.toString();
    }

    private Long getLong(Map<String, Object> row, String key) {
        Object value = row.get(key);
        if (value instanceof Number number) {
            return number.longValue();
        }
        return Long.valueOf(String.valueOf(value));
    }

    private SyncedUser ensureUser(String userId, String password, String email, String roleCode) {
        if (password == null || password.isBlank()) {
            throw new IllegalStateException(roleCode + " 계정 비밀번호가 설정되어 있지 않습니다.");
        }

        Map<String, Object> user = loginDao.findByUserId(userId);
        boolean created = false;
        String passwordHash = passwordEncoder.encode(password);

        if (user == null) {
            Map<String, Object> params = new HashMap<>();
            params.put("userId", userId);
            params.put("passwordHash", passwordHash);
            params.put("email", email);
            params.put("roleCode", roleCode);
            params.put("otpEnabled", 0);
            params.put("accountStatus", "ACTIVE");
            devAdminDao.insertUser(params);
            created = true;
        } else {
            Map<String, Object> params = new HashMap<>();
            params.put("userSeq", getLong(user, "userSeq"));
            params.put("passwordHash", passwordHash);
            params.put("email", email);
            params.put("roleCode", roleCode);
            params.put("otpEnabled", 0);
            params.put("accountStatus", "ACTIVE");
            devAdminDao.updateUser(params);
        }

        user = loginDao.findByUserId(userId);
        Long userSeq = getLong(user, "userSeq");
        devAdminDao.ensureRandomLocalRule(userSeq);
        devAdminDao.ensureHotClaudeRule(userSeq);
        return new SyncedUser(user, created);
    }

    private record SyncedUser(Map<String, Object> user, boolean created) {
    }
}
