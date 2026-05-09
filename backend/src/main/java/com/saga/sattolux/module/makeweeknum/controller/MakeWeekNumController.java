package com.saga.sattolux.module.makeweeknum.controller;

import com.saga.sattolux.module.makeweeknum.dto.GenerationRuleResponse;
import com.saga.sattolux.module.makeweeknum.dto.GenerateNumbersResponse;
import com.saga.sattolux.module.makeweeknum.dto.MakeWeekNumStatusResponse;
import com.saga.sattolux.module.makeweeknum.dto.SattoNumberSetResponse;
import com.saga.sattolux.module.makeweeknum.service.MakeWeekNumService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/api/make-week-num")
@RequiredArgsConstructor
public class MakeWeekNumController {

    private final MakeWeekNumService makeWeekNumService;

    @GetMapping("/rules")
    public ResponseEntity<List<GenerationRuleResponse>> getRules(Authentication authentication) {
        Long userSeq = (Long) authentication.getPrincipal();
        return ResponseEntity.ok(makeWeekNumService.getActiveRules(userSeq));
    }

    @PostMapping("/generate")
    public ResponseEntity<GenerateNumbersResponse> generateCurrentWeek(Authentication authentication,
                                                                      @RequestParam(defaultValue = "false") boolean force) {
        requireCreateNumPermission(authentication);
        Long userSeq = (Long) authentication.getPrincipal();
        return ResponseEntity.ok(makeWeekNumService.generateCurrentWeekNumbers(userSeq, force));
    }

    @PostMapping("/manual-generate")
    public ResponseEntity<GenerateNumbersResponse> generateManualCurrentWeek(Authentication authentication) {
        requireCreateNumPermission(authentication);
        Long userSeq = (Long) authentication.getPrincipal();
        return ResponseEntity.ok(makeWeekNumService.generateManualCurrentWeekNumbers(userSeq));
    }

    private void requireCreateNumPermission(Authentication authentication) {
        boolean isAdmin = authentication.getAuthorities().stream()
                .anyMatch(a -> "ROLE_ADMIN".equals(a.getAuthority()));
        if (isAdmin) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "번호 생성 권한이 없습니다.");
        }
    }

    @GetMapping("/current-week")
    public ResponseEntity<List<SattoNumberSetResponse>> getCurrentWeekNumbers(Authentication authentication) {
        Long userSeq = (Long) authentication.getPrincipal();
        return ResponseEntity.ok(makeWeekNumService.getCurrentWeekNumbers(userSeq));
    }

    @GetMapping("/status")
    public ResponseEntity<MakeWeekNumStatusResponse> getCurrentWeekStatus(Authentication authentication) {
        Long userSeq = (Long) authentication.getPrincipal();
        return ResponseEntity.ok(makeWeekNumService.getCurrentWeekStatus(userSeq));
    }
}
