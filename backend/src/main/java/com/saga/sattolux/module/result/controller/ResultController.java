package com.saga.sattolux.module.result.controller;

import com.saga.sattolux.module.result.dto.ResultCollectionResponse;
import com.saga.sattolux.module.result.dto.ResultHistoryItemResponse;
import com.saga.sattolux.module.result.dto.ResultManualTestPrepareResponse;
import com.saga.sattolux.module.result.dto.ResultManualTestResponse;
import com.saga.sattolux.module.result.dto.ResultWeekResponse;
import com.saga.sattolux.module.result.service.ResultService;
import java.time.LocalDateTime;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpStatus;
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
@RequestMapping("/api/result")
@RequiredArgsConstructor
public class ResultController {

    private final ResultService resultService;

    @GetMapping("/week")
    public ResponseEntity<ResultWeekResponse> getWeekResult(Authentication authentication,
                                                            @RequestParam(required = false) Integer year,
                                                            @RequestParam(required = false) Integer month,
                                                            @RequestParam(required = false) Integer week) {
        Long userSeq = (Long) authentication.getPrincipal();
        return ResponseEntity.ok(resultService.getWeekResult(userSeq, year, month, week));
    }

    @GetMapping("/history")
    public ResponseEntity<List<ResultHistoryItemResponse>> getResultHistory(Authentication authentication) {
        Long userSeq = (Long) authentication.getPrincipal();
        return ResponseEntity.ok(resultService.getResultHistory(userSeq));
    }

    @GetMapping("/history/{year}/{month}/{week}")
    public ResponseEntity<ResultWeekResponse> getResultHistoryDetail(Authentication authentication,
                                                                     @PathVariable int year,
                                                                     @PathVariable int month,
                                                                     @PathVariable int week) {
        Long userSeq = (Long) authentication.getPrincipal();
        return ResponseEntity.ok(resultService.getResultHistoryDetail(userSeq, year, month, week));
    }

    @PostMapping("/admin/collect-now")
    public ResponseEntity<ResultCollectionResponse> collectNow(Authentication authentication) {
        requireAdmin(authentication);
        int savedCount = resultService.collectLatestResults();
        return ResponseEntity.ok(new ResultCollectionResponse(savedCount, LocalDateTime.now()));
    }

    @PostMapping("/admin/manual-test-latest")
    public ResponseEntity<ResultManualTestResponse> runManualTest(Authentication authentication) {
        requireAdmin(authentication);
        return ResponseEntity.ok(resultService.runLatestResultManualTest());
    }

    @PostMapping("/admin/manual-test-prepare")
    public ResponseEntity<ResultManualTestPrepareResponse> prepareManualTest(Authentication authentication) {
        requireAdmin(authentication);
        Long userSeq = (Long) authentication.getPrincipal();
        return ResponseEntity.ok(resultService.prepareLatestResultManualTestData(userSeq));
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
