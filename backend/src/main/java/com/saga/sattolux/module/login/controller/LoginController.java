package com.saga.sattolux.module.login.controller;

import com.saga.sattolux.module.login.dto.LoginRequest;
import com.saga.sattolux.module.login.dto.LoginResponse;
import com.saga.sattolux.module.login.dto.LogoutRequest;
import com.saga.sattolux.module.login.dto.OtpSendRequest;
import com.saga.sattolux.module.login.dto.OtpVerifyRequest;
import com.saga.sattolux.module.login.dto.RefreshRequest;
import com.saga.sattolux.module.login.dto.RsaKeyResponse;
import com.saga.sattolux.module.login.dto.TokenIssueRequest;
import com.saga.sattolux.module.login.dto.TokenResponse;
import com.saga.sattolux.module.login.dto.UserMeResponse;
import com.saga.sattolux.module.login.service.LoginService;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class LoginController {

    private final LoginService loginService;

    // 1. RSA 공개키 발급
    @GetMapping("/rsa-key")
    public ResponseEntity<RsaKeyResponse> getRsaKey() throws Exception {
        return ResponseEntity.ok(loginService.generateRsaKey());
    }

    // 2. 1차 인증 (비밀번호)
    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@Valid @RequestBody LoginRequest request) throws Exception {
        return ResponseEntity.ok(loginService.login(request));
    }

    // 3. 2차 인증 OTP 전송
    @PostMapping("/otp/send")
    public ResponseEntity<Void> sendOtp(@Valid @RequestBody OtpSendRequest request) {
        loginService.sendOtp(request.authSessionToken());
        return ResponseEntity.ok().build();
    }

    // 4. OTP 검증 + JWT 발급
    @PostMapping("/otp/verify")
    public ResponseEntity<TokenResponse> verifyOtp(@Valid @RequestBody OtpVerifyRequest request,
                                                   HttpServletRequest httpServletRequest) {
        return ResponseEntity.ok(loginService.verifyOtpAndIssueToken(
                request.authSessionToken(),
                request.code(),
                httpServletRequest.getRemoteAddr(),
                httpServletRequest.getHeader("User-Agent")
        ));
    }

    // 5. OTP Skip + JWT 직접 발급 (otp_enabled=false 사용자)
    @PostMapping("/token")
    public ResponseEntity<TokenResponse> issueToken(@Valid @RequestBody TokenIssueRequest request,
                                                    HttpServletRequest httpServletRequest) {
        return ResponseEntity.ok(loginService.issueToken(
                request.authSessionToken(),
                httpServletRequest.getRemoteAddr(),
                httpServletRequest.getHeader("User-Agent")
        ));
    }

    // 6. Access Token 갱신 (Refresh Token 로테이션)
    @PostMapping("/refresh")
    public ResponseEntity<TokenResponse> refresh(@Valid @RequestBody RefreshRequest request,
                                                 HttpServletRequest httpServletRequest) {
        return ResponseEntity.ok(loginService.refresh(
                request.refreshToken(),
                httpServletRequest.getRemoteAddr(),
                httpServletRequest.getHeader("User-Agent")
        ));
    }

    // 7. 로그아웃
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@Valid @RequestBody LogoutRequest request) {
        loginService.logout(request.refreshToken());
        return ResponseEntity.ok().build();
    }

    @GetMapping("/me")
    public ResponseEntity<UserMeResponse> me(Authentication authentication) {
        Long userSeq = (Long) authentication.getPrincipal();
        return ResponseEntity.ok(loginService.getCurrentUser(userSeq));
    }

    @GetMapping(path = "/sse", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter connectSse(Authentication authentication,
                                 @RequestParam(required = false) String accessToken,
                                 @RequestHeader(value = HttpHeaders.AUTHORIZATION, required = false) String authorizationHeader) {
        Long userSeq = (Long) authentication.getPrincipal();
        return loginService.connectSse(userSeq, resolveAccessToken(accessToken, authorizationHeader));
    }

    private String resolveAccessToken(String accessToken, String authorizationHeader) {
        if (accessToken != null && !accessToken.isBlank()) {
            return accessToken;
        }

        if (authorizationHeader != null && authorizationHeader.startsWith("Bearer ")) {
            return authorizationHeader.substring(7);
        }

        throw new IllegalArgumentException("Access Token이 필요합니다.");
    }
}
