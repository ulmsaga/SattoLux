package com.saga.sattolux.module.login.service.impl;

import com.saga.sattolux.module.login.dao.DevAdminDao;
import com.saga.sattolux.module.login.dao.LoginDao;
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

    @Value("${GENERAL_LOGIN_USER:${LOGIN_USER}_user}")
    private String generalLoginUser;

    @Value("${GENERAL_LOGIN_PW:}")
    private String generalLoginPassword;

    @Value("${GENERAL_LOGIN_EMAIL:${GENERAL_LOGIN_USER:${LOGIN_USER}_user}@sattolux.local}")
    private String generalLoginEmail;

    @Override
    @Transactional
    public DevUserEnsureResponse ensureGeneralUser() {
        if (generalLoginPassword == null || generalLoginPassword.isBlank()) {
            throw new IllegalStateException("GENERAL_LOGIN_PW must be configured to create a dev general user.");
        }

        Map<String, Object> user = loginDao.findByUserId(generalLoginUser);
        boolean created = false;

        if (user == null) {
            Map<String, Object> params = new HashMap<>();
            params.put("userId", generalLoginUser);
            params.put("passwordHash", passwordEncoder.encode(generalLoginPassword));
            params.put("email", generalLoginEmail);
            params.put("roleCode", "USER");
            params.put("otpEnabled", 0);
            params.put("accountStatus", "ACTIVE");
            devAdminDao.insertUser(params);
            user = loginDao.findByUserId(generalLoginUser);
            created = true;
        }

        Long userSeq = getLong(user, "userSeq");
        devAdminDao.ensureRandomLocalRule(userSeq);
        devAdminDao.ensureHotClaudeRule(userSeq);

        return new DevUserEnsureResponse(
                getString(user, "userId"),
                getString(user, "email"),
                getString(user, "roleCode"),
                created,
                true
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
}
