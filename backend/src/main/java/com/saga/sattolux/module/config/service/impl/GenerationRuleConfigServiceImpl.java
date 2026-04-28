package com.saga.sattolux.module.config.service.impl;

import com.saga.sattolux.module.config.dao.GenerationRuleConfigDao;
import com.saga.sattolux.module.config.dto.GenerationRuleSaveRequest;
import com.saga.sattolux.module.config.dto.GenerationRuleUpsertRequest;
import com.saga.sattolux.module.config.service.GenerationRuleConfigService;
import com.saga.sattolux.module.makeweeknum.dto.GenerationRuleResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class GenerationRuleConfigServiceImpl implements GenerationRuleConfigService {

    private static final Set<Integer> ALLOWED_WEEKLY_SET_TOTALS = Set.of(1, 5, 10, 15, 20);
    private static final Set<String> HOT_NUMBER_ENGINES = Set.of("LOCAL", "CLAUDE");

    private final GenerationRuleConfigDao generationRuleConfigDao;

    @Override
    public List<GenerationRuleResponse> getRules(Long userSeq) {
        return generationRuleConfigDao.findRulesByUserSeq(userSeq)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Override
    @Transactional
    public List<GenerationRuleResponse> saveRules(Long userSeq, GenerationRuleSaveRequest request) {
        List<GenerationRuleUpsertRequest> rules = request == null || request.rules() == null
                ? List.of()
                : request.rules();

        validateRules(rules);

        List<Map<String, Object>> existingRules = generationRuleConfigDao.findRulesByUserSeq(userSeq);
        Set<Long> existingRuleIds = existingRules.stream()
                .map(row -> getLong(row, "ruleId"))
                .collect(java.util.stream.Collectors.toSet());

        Set<Long> submittedRuleIds = new HashSet<>();
        int sortOrder = 1;
        for (GenerationRuleUpsertRequest rule : rules) {
            Map<String, Object> params = toParams(userSeq, sortOrder++, rule);
            if (rule.ruleId() != null) {
                if (!existingRuleIds.contains(rule.ruleId())) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "유효하지 않은 ruleId가 포함되어 있습니다.");
                }
                submittedRuleIds.add(rule.ruleId());
                generationRuleConfigDao.updateRule(params);
            } else {
                generationRuleConfigDao.insertRule(params);
            }
        }

        for (Long existingRuleId : existingRuleIds) {
            if (!submittedRuleIds.contains(existingRuleId)) {
                generationRuleConfigDao.deleteRule(userSeq, existingRuleId);
            }
        }

        return getRules(userSeq);
    }

    private void validateRules(List<GenerationRuleUpsertRequest> rules) {
        if (rules.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "최소 1개의 규칙이 필요합니다.");
        }

        Integer activeDayOfWeek = null;
        int activeSetTotal = 0;
        for (GenerationRuleUpsertRequest rule : rules) {
            validateRule(rule);

            if ("Y".equals(rule.useYn())) {
                activeSetTotal += rule.setCount();
                if (activeDayOfWeek == null) {
                    activeDayOfWeek = rule.dayOfWeek();
                } else if (!activeDayOfWeek.equals(rule.dayOfWeek())) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "활성 규칙의 생성 요일은 모두 같아야 합니다.");
                }
            }
        }

        if (!ALLOWED_WEEKLY_SET_TOTALS.contains(activeSetTotal)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "활성 규칙의 주간 총 세트 수는 1, 5, 10, 15, 20 중 하나여야 합니다.");
        }
    }

    private void validateRule(GenerationRuleUpsertRequest rule) {
        if (rule == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "비어 있는 규칙은 저장할 수 없습니다.");
        }

        if (rule.dayOfWeek() == null || rule.dayOfWeek() < 1 || rule.dayOfWeek() > 5) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "생성 요일은 월요일부터 금요일까지만 선택할 수 있습니다.");
        }

        if (rule.setCount() == null || rule.setCount() < 1 || rule.setCount() > 20) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "세트 수는 1 이상 20 이하로 입력해야 합니다.");
        }

        if (!"Y".equals(rule.useYn()) && !"N".equals(rule.useYn())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "사용 여부 값이 올바르지 않습니다.");
        }

        if ("MIXED".equals(rule.methodCode())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "MIXED 전략은 현재 범위에서 제외되어 있습니다. 차기 업그레이드 시 지원 예정입니다.");
        }

        if ("RANDOM".equals(rule.methodCode())) {
            if (!"LOCAL".equals(rule.generatorCode())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "RANDOM 전략은 현재 LOCAL 엔진만 지원합니다.");
            }
            return;
        }

        if ("HOT_NUMBER".equals(rule.methodCode())) {
            if (!HOT_NUMBER_ENGINES.contains(rule.generatorCode())) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "HOT_NUMBER 전략의 생성 엔진 값이 올바르지 않습니다.");
            }
            if (rule.analysisDrawCount() == null || rule.analysisDrawCount() < 1) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "HOT_NUMBER 전략은 분석 회차 수를 1 이상 입력해야 합니다.");
            }
            return;
        }

        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "지원하지 않는 생성 전략입니다.");
    }

    private Map<String, Object> toParams(Long userSeq, int sortOrder, GenerationRuleUpsertRequest rule) {
        Map<String, Object> params = new HashMap<>();
        params.put("ruleId", rule.ruleId());
        params.put("userSeq", userSeq);
        params.put("dayOfWeek", rule.dayOfWeek());
        params.put("methodCode", rule.methodCode());
        params.put("generatorCode", rule.generatorCode());
        params.put("setCount", rule.setCount());
        params.put("analysisDrawCount", "HOT_NUMBER".equals(rule.methodCode()) ? rule.analysisDrawCount() : null);
        params.put("sortOrder", sortOrder);
        params.put("useYn", rule.useYn());
        return params;
    }

    private GenerationRuleResponse toResponse(Map<String, Object> row) {
        return new GenerationRuleResponse(
                getLong(row, "ruleId"),
                getInt(row, "dayOfWeek"),
                getString(row, "methodCode"),
                getString(row, "generatorCode"),
                getInt(row, "setCount"),
                row.get("analysisDrawCount") == null ? null : getInt(row, "analysisDrawCount"),
                getInt(row, "sortOrder"),
                getString(row, "useYn")
        );
    }

    private String getString(Map<String, Object> row, String key) {
        Object value = row.get(key);
        return value == null ? null : value.toString();
    }

    private Long getLong(Map<String, Object> row, String key) {
        Object value = row.get(key);
        if (value == null) {
            return null;
        }
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
}
