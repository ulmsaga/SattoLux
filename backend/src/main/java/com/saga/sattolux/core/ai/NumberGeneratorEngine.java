package com.saga.sattolux.core.ai;

public interface NumberGeneratorEngine {
    boolean supports(String methodCode, String generatorCode);
    NumberGenerationResult generate(NumberGenerationRequest request);
}
