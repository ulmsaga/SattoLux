package com.saga.sattolux.module.notification.controller;

import com.saga.sattolux.module.notification.dto.NotificationReplayResponse;
import com.saga.sattolux.module.notification.dto.NotificationSummaryResponse;
import com.saga.sattolux.module.notification.service.NotificationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/api/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    public ResponseEntity<NotificationSummaryResponse> getNotifications(Authentication authentication) {
        Long userSeq = (Long) authentication.getPrincipal();
        return ResponseEntity.ok(notificationService.getNotifications(userSeq));
    }

    @PostMapping("/{notificationId}/read")
    public ResponseEntity<Void> markRead(Authentication authentication,
                                         @PathVariable Long notificationId) {
        Long userSeq = (Long) authentication.getPrincipal();
        notificationService.markRead(userSeq, notificationId);
        return ResponseEntity.ok().build();
    }

    @PostMapping("/admin/replay-result-ready")
    public ResponseEntity<NotificationReplayResponse> replayResultReady(Authentication authentication,
                                                                        @RequestParam Integer year,
                                                                        @RequestParam Integer month,
                                                                        @RequestParam Integer week) {
        requireAdmin(authentication);
        Long userSeq = (Long) authentication.getPrincipal();
        return ResponseEntity.ok(notificationService.replayResultReadyNotification(userSeq, year, month, week));
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
