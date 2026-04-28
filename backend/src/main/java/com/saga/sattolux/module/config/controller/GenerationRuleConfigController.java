package com.saga.sattolux.module.config.controller;

import com.saga.sattolux.module.config.dto.GenerationRuleSaveRequest;
import com.saga.sattolux.module.config.service.GenerationRuleConfigService;
import com.saga.sattolux.module.makeweeknum.dto.GenerationRuleResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/config/generation-rules")
@RequiredArgsConstructor
public class GenerationRuleConfigController {

    private final GenerationRuleConfigService generationRuleConfigService;

    @GetMapping
    public ResponseEntity<List<GenerationRuleResponse>> getRules(Authentication authentication) {
        Long userSeq = (Long) authentication.getPrincipal();
        return ResponseEntity.ok(generationRuleConfigService.getRules(userSeq));
    }

    @PutMapping
    public ResponseEntity<List<GenerationRuleResponse>> saveRules(Authentication authentication,
                                                                 @RequestBody GenerationRuleSaveRequest request) {
        Long userSeq = (Long) authentication.getPrincipal();
        return ResponseEntity.ok(generationRuleConfigService.saveRules(userSeq, request));
    }
}
