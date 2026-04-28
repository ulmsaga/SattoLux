package com.saga.sattolux.module.login.controller;

import com.saga.sattolux.module.login.dto.DevUserEnsureResponse;
import com.saga.sattolux.module.login.service.DevAdminService;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Profile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@Profile("dev")
@RequestMapping("/api/auth/admin/dev-users")
@RequiredArgsConstructor
public class DevAdminController {

    private final DevAdminService devAdminService;

    @PostMapping("/ensure-general")
    public ResponseEntity<DevUserEnsureResponse> ensureGeneralUser(Authentication authentication) {
        requireAdmin(authentication);
        return ResponseEntity.ok(devAdminService.ensureGeneralUser());
    }

    private void requireAdmin(Authentication authentication) {
        boolean admin = authentication.getAuthorities()
                .stream()
                .map(GrantedAuthority::getAuthority)
                .anyMatch("ROLE_ADMIN"::equals);

        if (!admin) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "ADMIN 권한이 필요합니다.");
        }
    }
}
