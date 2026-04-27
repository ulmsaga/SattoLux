package com.saga.sattolux.module.makeweeknum.service.impl;

import com.saga.sattolux.core.ai.NumberGenerationRequest;
import com.saga.sattolux.core.ai.NumberGenerationResult;
import com.saga.sattolux.core.ai.NumberGeneratorEngine;
import com.saga.sattolux.module.makeweeknum.dao.MakeWeekNumDao;
import com.saga.sattolux.module.makeweeknum.dto.GenerationRuleResponse;
import com.saga.sattolux.module.makeweeknum.dto.GenerateNumbersResponse;
import com.saga.sattolux.module.makeweeknum.dto.SattoNumberSetResponse;
import com.saga.sattolux.module.makeweeknum.dto.SkippedRuleResponse;
import com.saga.sattolux.module.makeweeknum.service.MakeWeekNumService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.temporal.WeekFields;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

@Service
@Slf4j
@RequiredArgsConstructor
public class MakeWeekNumServiceImpl implements MakeWeekNumService {

    private final MakeWeekNumDao makeWeekNumDao;
    private final List<NumberGeneratorEngine> numberGeneratorEngines;

    @Override
    public List<GenerationRuleResponse> getActiveRules(Long userSeq) {
        return makeWeekNumDao.findActiveRules(userSeq)
                .stream()
                .map(this::toRuleResponse)
                .toList();
    }

    @Override
    @Transactional
    public GenerateNumbersResponse generateCurrentWeekNumbers(Long userSeq, boolean force) {
        LocalDate today = LocalDate.now();
        int targetYear = today.getYear();
        int targetMonth = today.getMonthValue();
        int targetWeekOfMonth = weekOfMonth(today);

        List<Map<String, Object>> activeRules = makeWeekNumDao.findActiveRules(userSeq);
        List<SattoNumberSetResponse> generatedSets = new ArrayList<>();
        List<SkippedRuleResponse> skippedRules = new ArrayList<>();

        for (Map<String, Object> rule : activeRules) {
            Long ruleId = getLong(rule, "ruleId");
            String methodCode = getString(rule, "methodCode");
            String generatorCode = getString(rule, "generatorCode");

            if (!force && !isTodayRule(rule, today)) {
                skippedRules.add(new SkippedRuleResponse(ruleId, methodCode, generatorCode, "오늘 실행 대상 요일이 아닙니다."));
                continue;
            }

            RuleGenerationOutcome outcome = generateRuleSets(rule);
            if (outcome.skippedReason() != null) {
                skippedRules.add(new SkippedRuleResponse(ruleId, methodCode, generatorCode, outcome.skippedReason()));
                continue;
            }

            makeWeekNumDao.deleteNumberSetsForRuleScope(userSeq, ruleId, targetYear, targetMonth, targetWeekOfMonth);
            for (GeneratedSet generatedSet : outcome.generatedSets()) {
                Map<String, Object> params = new HashMap<>();
                params.put("userSeq", userSeq);
                params.put("ruleId", ruleId);
                params.put("targetYear", targetYear);
                params.put("targetMonth", targetMonth);
                params.put("targetWeekOfMonth", targetWeekOfMonth);
                params.put("drawNo", null);
                params.put("methodCode", generatedSet.methodCode());
                params.put("generatorCode", generatedSet.generatorCode());
                params.put("no1", generatedSet.numbers().get(0));
                params.put("no2", generatedSet.numbers().get(1));
                params.put("no3", generatedSet.numbers().get(2));
                params.put("no4", generatedSet.numbers().get(3));
                params.put("no5", generatedSet.numbers().get(4));
                params.put("no6", generatedSet.numbers().get(5));
                makeWeekNumDao.saveNumberSet(params);
            }
        }

        generatedSets.addAll(makeWeekNumDao.findNumberSetsByScope(userSeq, targetYear, targetMonth, targetWeekOfMonth)
                .stream()
                .map(this::toNumberSetResponse)
                .toList());

        return new GenerateNumbersResponse(
                targetYear,
                targetMonth,
                targetWeekOfMonth,
                generatedSets.size(),
                generatedSets,
                skippedRules
        );
    }

    @Override
    public List<SattoNumberSetResponse> getCurrentWeekNumbers(Long userSeq) {
        LocalDate today = LocalDate.now();
        return makeWeekNumDao.findNumberSetsByScope(userSeq, today.getYear(), today.getMonthValue(), weekOfMonth(today))
                .stream()
                .map(this::toNumberSetResponse)
                .toList();
    }

    private boolean isTodayRule(Map<String, Object> rule, LocalDate today) {
        int ruleDayOfWeek = getInt(rule, "dayOfWeek");
        return toRuleDayValue(today.getDayOfWeek()) == ruleDayOfWeek;
    }

    private int toRuleDayValue(DayOfWeek dayOfWeek) {
        return switch (dayOfWeek) {
            case MONDAY -> 1;
            case TUESDAY -> 2;
            case WEDNESDAY -> 3;
            case THURSDAY -> 4;
            case FRIDAY -> 5;
            case SATURDAY -> 6;
            case SUNDAY -> 7;
        };
    }

    private int weekOfMonth(LocalDate date) {
        return date.get(WeekFields.of(Locale.KOREA).weekOfMonth());
    }

    private RuleGenerationOutcome generateRuleSets(Map<String, Object> rule) {
        String methodCode = getString(rule, "methodCode");
        String generatorCode = getString(rule, "generatorCode");
        int setCount = getInt(rule, "setCount");

        if ("RANDOM".equals(methodCode) && "LOCAL".equals(generatorCode)) {
            return new RuleGenerationOutcome(generateRandomSetBatch(setCount), null);
        }

        if ("HOT_NUMBER".equals(methodCode)) {
            return generateHotNumberRule(rule, setCount);
        }

        if ("MIXED".equals(methodCode)) {
            return new RuleGenerationOutcome(List.of(), "MIXED 전략은 아직 구현 전입니다.");
        }

        return new RuleGenerationOutcome(List.of(), "지원하지 않는 생성 규칙입니다.");
    }

    private RuleGenerationOutcome generateHotNumberRule(Map<String, Object> rule, int setCount) {
        Integer analysisDrawCount = rowIntOrNull(rule, "analysisDrawCount");
        int targetDrawCount = analysisDrawCount == null || analysisDrawCount < 1 ? 100 : analysisDrawCount;
        List<Map<String, Object>> recentDraws = makeWeekNumDao.findRecentDrawResults(targetDrawCount);
        if (recentDraws.isEmpty()) {
            return new RuleGenerationOutcome(List.of(), "분석 대상 추첨 이력이 없습니다.");
        }

        Map<Integer, Integer> frequencyMap = buildFrequencyMap(recentDraws);
        List<Integer> topFrequencyNumbers = frequencyMap.entrySet()
                .stream()
                .sorted((left, right) -> {
                    int compare = Integer.compare(right.getValue(), left.getValue());
                    return compare != 0 ? compare : Integer.compare(left.getKey(), right.getKey());
                })
                .limit(10)
                .map(Map.Entry::getKey)
                .toList();

        try {
            NumberGeneratorEngine engine = findNumberGeneratorEngine(getString(rule, "methodCode"), getString(rule, "generatorCode"));
            if (engine == null) {
                throw new IllegalStateException("No matching number generator engine.");
            }

            NumberGenerationRequest request = new NumberGenerationRequest(
                    getLong(rule, "ruleId"),
                    getString(rule, "methodCode"),
                    getString(rule, "generatorCode"),
                    setCount,
                    targetDrawCount,
                    topFrequencyNumbers
            );
            NumberGenerationResult generationResult = engine.generate(request);
            List<GeneratedSet> generatedSets = validateGeneratedSets(
                    generationResult.sets(),
                    getString(rule, "methodCode"),
                    getString(rule, "generatorCode"),
                    setCount
            );
            return new RuleGenerationOutcome(generatedSets, null);
        } catch (Exception e) {
            log.warn("HOT_NUMBER generation failed for ruleId={}, fallback to LOCAL engine.",
                    getLong(rule, "ruleId"), e);
            return new RuleGenerationOutcome(generateLocalHotNumberSetBatch(setCount, frequencyMap), null);
        }
    }

    private NumberGeneratorEngine findNumberGeneratorEngine(String methodCode, String generatorCode) {
        return numberGeneratorEngines.stream()
                .filter(engine -> engine.supports(methodCode, generatorCode))
                .findFirst()
                .orElse(null);
    }

    private Map<Integer, Integer> buildFrequencyMap(List<Map<String, Object>> recentDraws) {
        Map<Integer, Integer> frequencyMap = IntStream.rangeClosed(1, 45)
                .boxed()
                .collect(Collectors.toMap(number -> number, number -> 0));

        for (Map<String, Object> draw : recentDraws) {
            incrementFrequency(frequencyMap, getInt(draw, "no1"));
            incrementFrequency(frequencyMap, getInt(draw, "no2"));
            incrementFrequency(frequencyMap, getInt(draw, "no3"));
            incrementFrequency(frequencyMap, getInt(draw, "no4"));
            incrementFrequency(frequencyMap, getInt(draw, "no5"));
            incrementFrequency(frequencyMap, getInt(draw, "no6"));
        }
        return frequencyMap;
    }

    private void incrementFrequency(Map<Integer, Integer> frequencyMap, int number) {
        frequencyMap.compute(number, (key, value) -> value == null ? 1 : value + 1);
    }

    private List<GeneratedSet> validateGeneratedSets(List<List<Integer>> rawSets,
                                                     String methodCode,
                                                     String generatorCode,
                                                     int expectedSetCount) {
        if (rawSets == null || rawSets.size() != expectedSetCount) {
            throw new IllegalStateException("Unexpected number of generated sets.");
        }

        Set<String> uniqueSetKeys = new HashSet<>();
        List<GeneratedSet> validatedSets = new ArrayList<>();
        for (List<Integer> rawSet : rawSets) {
            List<Integer> numbers = normalizeNumbers(rawSet);
            String setKey = numbers.stream().map(String::valueOf).collect(Collectors.joining(","));
            if (!uniqueSetKeys.add(setKey)) {
                throw new IllegalStateException("Duplicate number set detected.");
            }
            validatedSets.add(new GeneratedSet(numbers, methodCode, generatorCode));
        }
        return validatedSets;
    }

    private List<GeneratedSet> generateRandomSetBatch(int setCount) {
        List<GeneratedSet> generatedSets = new ArrayList<>();
        for (int i = 0; i < setCount; i++) {
            generatedSets.add(new GeneratedSet(generateRandomNumbers(), "RANDOM", "LOCAL"));
        }
        return generatedSets;
    }

    private List<GeneratedSet> generateLocalHotNumberSetBatch(int setCount, Map<Integer, Integer> frequencyMap) {
        List<GeneratedSet> generatedSets = new ArrayList<>();
        for (int i = 0; i < setCount; i++) {
            generatedSets.add(new GeneratedSet(generateHotNumbers(frequencyMap), "HOT_NUMBER", "LOCAL"));
        }
        return generatedSets;
    }

    private List<Integer> generateRandomNumbers() {
        LinkedHashSet<Integer> uniqueNumbers = new LinkedHashSet<>();
        while (uniqueNumbers.size() < 6) {
            uniqueNumbers.add(ThreadLocalRandom.current().nextInt(1, 46));
        }

        List<Integer> numbers = new ArrayList<>(uniqueNumbers);
        Collections.sort(numbers);
        return numbers;
    }

    private List<Integer> generateHotNumbers(Map<Integer, Integer> frequencyMap) {
        Map<Integer, Integer> remainingWeights = new HashMap<>(frequencyMap);
        List<Integer> numbers = new ArrayList<>();
        while (numbers.size() < 6) {
            int pickedNumber = pickWeightedNumber(remainingWeights);
            numbers.add(pickedNumber);
            remainingWeights.remove(pickedNumber);
        }

        Collections.sort(numbers);
        return numbers;
    }

    private int pickWeightedNumber(Map<Integer, Integer> weights) {
        int totalWeight = weights.values().stream()
                .mapToInt(weight -> Math.max(weight, 1))
                .sum();

        int randomValue = ThreadLocalRandom.current().nextInt(totalWeight) + 1;
        int cumulativeWeight = 0;
        for (Map.Entry<Integer, Integer> entry : weights.entrySet()) {
            cumulativeWeight += Math.max(entry.getValue(), 1);
            if (randomValue <= cumulativeWeight) {
                return entry.getKey();
            }
        }

        throw new IllegalStateException("Failed to select a weighted hot number.");
    }

    private List<Integer> normalizeNumbers(List<Integer> rawNumbers) {
        if (rawNumbers == null || rawNumbers.size() != 6) {
            throw new IllegalStateException("Each number set must contain exactly 6 numbers.");
        }

        Set<Integer> uniqueNumbers = new HashSet<>();
        List<Integer> numbers = new ArrayList<>();
        for (Integer rawNumber : rawNumbers) {
            if (rawNumber == null || rawNumber < 1 || rawNumber > 45) {
                throw new IllegalStateException("Generated number is outside the valid range.");
            }
            if (!uniqueNumbers.add(rawNumber)) {
                throw new IllegalStateException("Generated number set contains duplicates.");
            }
            numbers.add(rawNumber);
        }

        Collections.sort(numbers);
        return numbers;
    }

    private GenerationRuleResponse toRuleResponse(Map<String, Object> row) {
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

    private SattoNumberSetResponse toNumberSetResponse(Map<String, Object> row) {
        return new SattoNumberSetResponse(
                getLong(row, "setId"),
                getLong(row, "ruleId"),
                getInt(row, "targetYear"),
                getInt(row, "targetMonth"),
                getInt(row, "targetWeekOfMonth"),
                row.get("drawNo") == null ? null : getInt(row, "drawNo"),
                getString(row, "methodCode"),
                getString(row, "generatorCode"),
                List.of(
                        getInt(row, "no1"),
                        getInt(row, "no2"),
                        getInt(row, "no3"),
                        getInt(row, "no4"),
                        getInt(row, "no5"),
                        getInt(row, "no6")
                ),
                (LocalDateTime) row.get("createdAt")
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

    private Integer rowIntOrNull(Map<String, Object> row, String key) {
        Object value = row.get(key);
        if (value == null) {
            return null;
        }
        if (value instanceof Number number) {
            return number.intValue();
        }
        return Integer.valueOf(String.valueOf(value));
    }

    private record GeneratedSet(List<Integer> numbers, String methodCode, String generatorCode) {
    }

    private record RuleGenerationOutcome(List<GeneratedSet> generatedSets, String skippedReason) {
    }
}
