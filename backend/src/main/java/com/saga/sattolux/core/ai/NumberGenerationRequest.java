package com.saga.sattolux.core.ai;

import java.util.List;

public record NumberGenerationRequest(
        Long ruleId,
        String methodCode,
        String generatorCode,
        int setCount,
        Integer analysisDrawCount,
        List<Integer> topFrequencyNumbers
) {
}
