package com.saga.sattolux.module.result.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.saga.sattolux.core.auth.SseConnectionManager;
import com.saga.sattolux.module.makeweeknum.dto.GenerateNumbersResponse;
import com.saga.sattolux.module.makeweeknum.service.GenerationSchedulePolicy;
import com.saga.sattolux.module.makeweeknum.service.MakeWeekNumService;
import com.saga.sattolux.module.notification.dao.NotificationDao;
import com.saga.sattolux.module.result.dto.ResultManualTestPrepareResponse;
import com.saga.sattolux.module.result.dao.ResultDao;
import com.saga.sattolux.module.result.dto.ResultSetItemResponse;
import com.saga.sattolux.module.result.dto.ResultWeekResponse;
import com.saga.sattolux.module.result.dto.ResultManualTestResponse;
import com.saga.sattolux.module.result.service.ResultService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

@Service
@Slf4j
@RequiredArgsConstructor
public class ResultServiceImpl implements ResultService {

    private static final String RESULT_READY = "RESULT_READY";

    private final ResultDao resultDao;
    private final NotificationDao notificationDao;
    private final SseConnectionManager sseConnectionManager;
    private final GenerationSchedulePolicy generationSchedulePolicy;
    private final MakeWeekNumService makeWeekNumService;
    private final RestClient.Builder restClientBuilder;

    @Value("${satto.result-api}")
    private String resultApi;

    @Value("${satto.result-referer}")
    private String resultReferer;

    @Override
    @Transactional
    public int collectLatestResults() {
        int latestSavedDrawNo = resultDao.findLatestSavedDrawNo() == null ? 0 : resultDao.findLatestSavedDrawNo();
        int candidateDrawNo = latestSavedDrawNo + 1;
        int savedCount = 0;

        while (true) {
            DrawResultPayload payload = fetchDrawResult(candidateDrawNo);
            if (payload == null) {
                break;
            }

            saveDrawResultAndCompare(payload);
            savedCount++;
            candidateDrawNo++;
        }

        return savedCount;
    }

    @Override
    public ResultWeekResponse getWeekResult(Long userSeq, Integer year, Integer month, Integer weekOfMonth) {
        LocalDate today = generationSchedulePolicy.today();
        int targetYear = year == null ? today.getYear() : year;
        int targetMonth = month == null ? today.getMonthValue() : month;
        int targetWeek = weekOfMonth == null ? generationSchedulePolicy.weekOfMonth(today) : weekOfMonth;

        Map<String, Object> drawResult = resultDao.findWeekDrawResultByScope(targetYear, targetMonth, targetWeek);
        List<ResultSetItemResponse> items = resultDao.findMatchedSetsByScope(userSeq, targetYear, targetMonth, targetWeek)
                .stream()
                .map(this::toResultItem)
                .toList();

        if (drawResult == null) {
            return new ResultWeekResponse(targetYear, targetMonth, targetWeek, null, null, List.of(), null, items);
        }

        return new ResultWeekResponse(
                targetYear,
                targetMonth,
                targetWeek,
                getInt(drawResult, "drawNo"),
                toLocalDate(drawResult.get("drawDate")),
                List.of(
                        getInt(drawResult, "no1"),
                        getInt(drawResult, "no2"),
                        getInt(drawResult, "no3"),
                        getInt(drawResult, "no4"),
                        getInt(drawResult, "no5"),
                        getInt(drawResult, "no6")
                ),
                getInt(drawResult, "bonusNo"),
                items
        );
    }

    @Override
    @Transactional
    public ResultManualTestPrepareResponse prepareLatestResultManualTestData(Long userSeq) {
        Integer latestSavedDrawNo = resultDao.findLatestSavedDrawNo();
        if (latestSavedDrawNo == null || latestSavedDrawNo < 1) {
            throw new IllegalStateException("수동 테스트를 위한 기준 추첨 결과가 없습니다.");
        }

        DrawResultPayload payload = fetchLatestAvailableDrawResult(latestSavedDrawNo);
        if (payload == null) {
            throw new IllegalStateException("동행복권 API에서 최신 추첨 결과를 확인할 수 없습니다.");
        }

        GenerateNumbersResponse generated = makeWeekNumService.generateNumbersForDate(userSeq, payload.drawDate(), true);

        return new ResultManualTestPrepareResponse(
                payload.drawNo(),
                payload.drawDate(),
                payload.drawDate().getYear(),
                payload.drawDate().getMonthValue(),
                generationSchedulePolicy.weekOfMonth(payload.drawDate()),
                generated.generatedCount(),
                generationSchedulePolicy.nowLocalDateTime()
        );
    }

    @Override
    @Transactional
    public ResultManualTestResponse runLatestResultManualTest() {
        Integer latestSavedDrawNo = resultDao.findLatestSavedDrawNo();
        if (latestSavedDrawNo == null || latestSavedDrawNo < 1) {
            throw new IllegalStateException("수동 테스트를 위한 기준 추첨 결과가 없습니다.");
        }

        DrawResultPayload payload = fetchLatestAvailableDrawResult(latestSavedDrawNo);
        if (payload == null) {
            throw new IllegalStateException("동행복권 API에서 최신 추첨 결과를 확인할 수 없습니다.");
        }

        int targetYear = payload.drawDate().getYear();
        int targetMonth = payload.drawDate().getMonthValue();
        int targetWeek = generationSchedulePolicy.weekOfMonth(payload.drawDate());

        List<Map<String, Object>> sets = resultDao.findNumberSetsByScope(targetYear, targetMonth, targetWeek);
        if (sets.isEmpty()) {
            throw new IllegalArgumentException("최신 결과와 비교할 지난주 번호 세트가 없습니다.");
        }

        saveDrawResultAndCompare(payload, targetYear, targetMonth, targetWeek, sets);

        return new ResultManualTestResponse(
                payload.drawNo(),
                payload.drawDate(),
                targetYear,
                targetMonth,
                targetWeek,
                sets.size(),
                payload.drawNo() > latestSavedDrawNo,
                generationSchedulePolicy.nowLocalDateTime()
        );
    }

    @Transactional
    protected void saveDrawResultAndCompare(DrawResultPayload payload) {
        int targetYear = payload.drawDate().getYear();
        int targetMonth = payload.drawDate().getMonthValue();
        int targetWeek = generationSchedulePolicy.weekOfMonth(payload.drawDate());
        List<Map<String, Object>> sets = resultDao.findNumberSetsByScope(targetYear, targetMonth, targetWeek);
        saveDrawResultAndCompare(payload, targetYear, targetMonth, targetWeek, sets);
    }

    @Transactional
    protected void saveDrawResultAndCompare(DrawResultPayload payload,
                                            int targetYear,
                                            int targetMonth,
                                            int targetWeek,
                                            List<Map<String, Object>> sets) {
        resultDao.upsertDrawResult(Map.of(
                "drawNo", payload.drawNo(),
                "drawDate", payload.drawDate(),
                "no1", payload.numbers().get(0),
                "no2", payload.numbers().get(1),
                "no3", payload.numbers().get(2),
                "no4", payload.numbers().get(3),
                "no5", payload.numbers().get(4),
                "no6", payload.numbers().get(5),
                "bonusNo", payload.bonusNo()
        ));

        Map<String, Object> storedResult = resultDao.findDrawResultByDrawNo(payload.drawNo());
        if (storedResult == null) {
            throw new IllegalStateException("Stored draw result could not be reloaded.");
        }

        if (sets.isEmpty()) {
            return;
        }

        LinkedHashSet<Long> affectedUsers = new LinkedHashSet<>();
        for (Map<String, Object> set : sets) {
            List<Integer> setNumbers = List.of(
                    getInt(set, "no1"),
                    getInt(set, "no2"),
                    getInt(set, "no3"),
                    getInt(set, "no4"),
                    getInt(set, "no5"),
                    getInt(set, "no6")
            );

            int matchCount = (int) setNumbers.stream().filter(payload.numbers()::contains).count();
            boolean bonusMatch = setNumbers.contains(payload.bonusNo());
            Integer rank = determineRank(matchCount, bonusMatch);

            resultDao.updateNumberSetDrawNo(getLong(set, "setId"), payload.drawNo());
            Map<String, Object> params = new HashMap<>();
            params.put("setId", getLong(set, "setId"));
            params.put("resultId", getLong(storedResult, "resultId"));
            params.put("matchCount", matchCount);
            params.put("bonusMatch", bonusMatch ? 1 : 0);
            params.put("rank", rank);
            resultDao.upsertMatchResult(params);

            affectedUsers.add(getLong(set, "userSeq"));
        }

        for (Long userSeq : affectedUsers) {
            createAndPushResultReadyNotification(userSeq, payload.drawNo(), targetYear, targetMonth, targetWeek);
        }
    }

    private DrawResultPayload fetchLatestAvailableDrawResult(int latestSavedDrawNo) {
        DrawResultPayload latest = null;
        int candidateDrawNo = latestSavedDrawNo;

        while (true) {
            DrawResultPayload payload = fetchDrawResult(candidateDrawNo);
            if (payload == null) {
                break;
            }

            latest = payload;
            candidateDrawNo++;
        }

        return latest;
    }

    private void createAndPushResultReadyNotification(Long userSeq, int drawNo, int targetYear, int targetMonth, int targetWeek) {
        Map<String, Object> existing = notificationDao.findResultReadyNotification(userSeq, targetYear, targetMonth, targetWeek);
        if (existing != null) {
            return;
        }

        String title = "추첨 결과 도착";
        String message = String.format("%d년 %d월 %d주차 결과가 도착했습니다.", targetYear, targetMonth, targetWeek);

        notificationDao.insertNotification(Map.of(
                "userSeq", userSeq,
                "typeCode", RESULT_READY,
                "title", title,
                "message", message,
                "targetYear", targetYear,
                "targetMonth", targetMonth,
                "targetWeekOfMonth", targetWeek,
                "drawNo", drawNo
        ));

        Map<String, Object> inserted = notificationDao.findResultReadyNotification(userSeq, targetYear, targetMonth, targetWeek);
        if (inserted == null) {
            return;
        }

        sseConnectionManager.sendToUser(userSeq, "notification", Map.of(
                "type", RESULT_READY,
                "notificationId", getLong(inserted, "notificationId"),
                "title", title,
                "message", message,
                "targetYear", targetYear,
                "targetMonth", targetMonth,
                "targetWeekOfMonth", targetWeek,
                "drawNo", drawNo,
                "createdAt", inserted.get("createdAt").toString()
        ));
    }

    private DrawResultPayload fetchDrawResult(int drawNo) {
        RestClient client = restClientBuilder
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .defaultHeader(HttpHeaders.REFERER, resultReferer)
                .build();

        JsonNode response = client.get()
                .uri(resultApi + "?srchDir=center&srchLtEpsd=" + drawNo)
                .retrieve()
                .body(JsonNode.class);

        JsonNode list = response == null ? null : response.path("data").path("list");
        if (list == null || !list.isArray() || list.isEmpty()) {
            return null;
        }

        for (JsonNode item : list) {
            if (item.path("ltEpsd").asInt() != drawNo) {
                continue;
            }

            String rawDate = item.path("ltRflYmd").asText();
            if (!StringUtils.hasText(rawDate)) {
                return null;
            }

            List<Integer> numbers = new ArrayList<>();
            numbers.add(item.path("tm1WnNo").asInt());
            numbers.add(item.path("tm2WnNo").asInt());
            numbers.add(item.path("tm3WnNo").asInt());
            numbers.add(item.path("tm4WnNo").asInt());
            numbers.add(item.path("tm5WnNo").asInt());
            numbers.add(item.path("tm6WnNo").asInt());

            if (numbers.stream().anyMatch(number -> number < 1)) {
                return null;
            }

            return new DrawResultPayload(
                    drawNo,
                    LocalDate.parse(rawDate, DateTimeFormatter.BASIC_ISO_DATE),
                    numbers,
                    item.path("bnsWnNo").asInt()
            );
        }

        return null;
    }

    private Integer determineRank(int matchCount, boolean bonusMatch) {
        if (matchCount == 6) return 1;
        if (matchCount == 5 && bonusMatch) return 2;
        if (matchCount == 5) return 3;
        if (matchCount == 4) return 4;
        if (matchCount == 3) return 5;
        return null;
    }

    private ResultSetItemResponse toResultItem(Map<String, Object> row) {
        Object bonusMatchValue = row.get("bonusMatch");
        boolean bonusMatch = bonusMatchValue instanceof Number number
                ? number.intValue() == 1
                : "true".equalsIgnoreCase(String.valueOf(bonusMatchValue));

        return new ResultSetItemResponse(
                getLong(row, "setId"),
                getLong(row, "ruleId"),
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
                row.get("matchCount") == null ? null : getInt(row, "matchCount"),
                bonusMatch,
                row.get("rank") == null ? null : getInt(row, "rank")
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

    private int getInt(Map<String, Object> row, String key) {
        Object value = row.get(key);
        if (value instanceof Number number) {
            return number.intValue();
        }
        return Integer.parseInt(String.valueOf(value));
    }

    private LocalDate toLocalDate(Object value) {
        if (value instanceof LocalDate localDate) {
            return localDate;
        }
        if (value instanceof java.sql.Date sqlDate) {
            return sqlDate.toLocalDate();
        }
        if (value instanceof LocalDateTime localDateTime) {
            return localDateTime.toLocalDate();
        }
        return LocalDate.parse(String.valueOf(value));
    }

    private record DrawResultPayload(
            int drawNo,
            LocalDate drawDate,
            List<Integer> numbers,
            int bonusNo
    ) {
    }
}
