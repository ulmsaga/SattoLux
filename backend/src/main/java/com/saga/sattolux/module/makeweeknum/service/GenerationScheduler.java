package com.saga.sattolux.module.makeweeknum.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.DayOfWeek;
import java.time.ZonedDateTime;

@Component
@Slf4j
@RequiredArgsConstructor
public class GenerationScheduler {

    private final MakeWeekNumService makeWeekNumService;
    private final GenerationSchedulePolicy generationSchedulePolicy;

    @Scheduled(cron = "0 * * * * *", zone = "${satto.generation.scheduler.zone:Asia/Seoul}")
    public void generateScheduledNumbers() {
        ZonedDateTime now = generationSchedulePolicy.now();
        if (!generationSchedulePolicy.isExactScheduleMinute(now)) {
            return;
        }

        DayOfWeek today = now.getDayOfWeek();
        if (today == DayOfWeek.SATURDAY || today == DayOfWeek.SUNDAY) {
            return;
        }

        int generatedUserCount = makeWeekNumService.generateScheduledWeekNumbers();
        log.info("Scheduled weekly number generation completed. generatedUserCount={}, schedulerTime={}, zone={}",
                generatedUserCount, generationSchedulePolicy.schedulerTimeText(), generationSchedulePolicy.schedulerZoneText());
    }
}
