package com.saga.sattolux.module.makeweeknum.controller;

import com.saga.sattolux.module.makeweeknum.dto.GenerationRuleResponse;
import com.saga.sattolux.module.makeweeknum.dto.GenerateNumbersResponse;
import com.saga.sattolux.module.makeweeknum.dto.SattoNumberSetResponse;
import com.saga.sattolux.module.makeweeknum.service.MakeWeekNumService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
        Long userSeq = (Long) authentication.getPrincipal();
        return ResponseEntity.ok(makeWeekNumService.generateCurrentWeekNumbers(userSeq, force));
    }

    @GetMapping("/current-week")
    public ResponseEntity<List<SattoNumberSetResponse>> getCurrentWeekNumbers(Authentication authentication) {
        Long userSeq = (Long) authentication.getPrincipal();
        return ResponseEntity.ok(makeWeekNumService.getCurrentWeekNumbers(userSeq));
    }
}
