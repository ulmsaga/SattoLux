package com.saga.sattolux.module.result.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@Slf4j
@RequiredArgsConstructor
public class ResultCollectionScheduler {

    private final ResultService resultService;

    @Scheduled(cron = "${satto.result.scheduler.saturday-cron:0 */5 21-23 * * SAT}", zone = "${satto.generation.scheduler.zone:Asia/Seoul}")
    public void collectOnSaturday() {
        collect("saturday");
    }

    @Scheduled(cron = "${satto.result.scheduler.sunday-midnight-cron:0 0 0 * * SUN}", zone = "${satto.generation.scheduler.zone:Asia/Seoul}")
    public void collectOnSundayMidnight() {
        collect("sunday-00:00");
    }

    @Scheduled(cron = "${satto.result.scheduler.sunday-morning-cron:0 0 9 * * SUN}", zone = "${satto.generation.scheduler.zone:Asia/Seoul}")
    public void collectOnSundayMorning() {
        collect("sunday-09:00");
    }

    private void collect(String trigger) {
        int savedCount = resultService.collectLatestResults();
        log.info("Result collection scheduler completed. trigger={}, savedCount={}", trigger, savedCount);
    }
}
