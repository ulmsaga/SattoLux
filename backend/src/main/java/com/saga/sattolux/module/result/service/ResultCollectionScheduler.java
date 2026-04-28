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

    @Scheduled(cron = "${satto.result.scheduler.cron:0 */5 21-23 * * SAT}", zone = "${satto.generation.scheduler.zone:Asia/Seoul}")
    public void collect() {
        int savedCount = resultService.collectLatestResults();
        log.info("Result collection scheduler completed. savedCount={}", savedCount);
    }
}
