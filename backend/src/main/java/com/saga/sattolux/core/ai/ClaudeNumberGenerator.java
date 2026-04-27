package com.saga.sattolux.core.ai;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class ClaudeNumberGenerator implements NumberGeneratorEngine {

    private final RestClient.Builder restClientBuilder;
    private final ObjectMapper objectMapper;

    @Value("${ai.claude.api-key:}")
    private String apiKey;

    @Value("${ai.claude.model:claude-haiku-4-5-20251001}")
    private String model;

    @Value("${ai.claude.base-url:https://api.anthropic.com}")
    private String baseUrl;

    @Override
    public boolean supports(String methodCode, String generatorCode) {
        return "CLAUDE".equals(generatorCode) && ("HOT_NUMBER".equals(methodCode) || "MIXED".equals(methodCode));
    }

    @Override
    public NumberGenerationResult generate(NumberGenerationRequest request) {
        if (!StringUtils.hasText(apiKey)) {
            throw new IllegalStateException("Anthropic API key is not configured.");
        }

        RestClient client = restClientBuilder
                .baseUrl(baseUrl)
                .defaultHeader("x-api-key", apiKey)
                .defaultHeader("anthropic-version", "2023-06-01")
                .defaultHeader(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_JSON_VALUE)
                .build();

        Map<String, Object> payload = new HashMap<>();
        payload.put("model", model);
        payload.put("max_tokens", Math.max(512, request.setCount() * 128));
        payload.put("temperature", 1.0);
        payload.put("messages", List.of(
                Map.of("role", "user", "content", buildUserPrompt(request))
        ));

        JsonNode response = client.post()
                .uri("/v1/messages")
                .body(payload)
                .retrieve()
                .body(JsonNode.class);

        String outputText = extractOutputText(response);
        return parseResult(outputText, request.setCount());
    }

    private String buildUserPrompt(NumberGenerationRequest request) {
        int setCount = request.setCount();
        String freqList = request.topFrequencyNumbers().toString();

        String instruction = String.format("""
                You are a number picker. Return ONLY a minified JSON object — no markdown, no explanation.
                Required format: {"sets":[{"numbers":[n1,n2,n3,n4,n5,n6]}, ...]}
                Rules:
                - Output EXACTLY %d sets (the "sets" array must have %d elements).
                - Each set: exactly 6 unique integers from 1–45, sorted ascending.
                - Sets must not be identical to each other.
                - Favor numbers from this frequency list (most frequent first): %s
                """, setCount, setCount, freqList);

        return instruction;
    }

    private String extractOutputText(JsonNode response) {
        JsonNode content = response.path("content");
        if (!content.isArray()) {
            throw new IllegalStateException("Claude response does not contain a content array.");
        }

        for (JsonNode contentItem : content) {
            if ("text".equals(contentItem.path("type").asText()) && contentItem.hasNonNull("text")) {
                return contentItem.get("text").asText();
            }
        }

        throw new IllegalStateException("Claude response did not return text content.");
    }

    private NumberGenerationResult parseResult(String outputText, int expectedSetCount) {
        try {
            String jsonText = extractJsonPayload(outputText);
            JsonNode root = objectMapper.readTree(jsonText);
            JsonNode sets = root.path("sets");
            if (!sets.isArray() || sets.isEmpty()) {
                throw new IllegalStateException("Claude response returned no sets.");
            }

            List<List<Integer>> generatedSets = new ArrayList<>();
            for (JsonNode setNode : sets) {
                JsonNode numbersNode = setNode.path("numbers");
                if (!numbersNode.isArray() || numbersNode.size() != 6) {
                    throw new IllegalStateException("Claude response returned invalid numbers.");
                }

                List<Integer> numbers = new ArrayList<>();
                for (JsonNode numberNode : numbersNode) {
                    numbers.add(numberNode.asInt());
                }
                generatedSets.add(numbers);
            }

            if (generatedSets.size() > expectedSetCount && expectedSetCount == 1) {
                return new NumberGenerationResult(List.of(generatedSets.get(0)));
            }
            return new NumberGenerationResult(generatedSets);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to parse Claude structured output.", e);
        }
    }

    private String extractJsonPayload(String text) {
        String trimmed = text == null ? "" : text.trim();
        if (trimmed.startsWith("```")) {
            int firstNewline = trimmed.indexOf('\n');
            int lastFence = trimmed.lastIndexOf("```");
            if (firstNewline > 0 && lastFence > firstNewline) {
                trimmed = trimmed.substring(firstNewline + 1, lastFence).trim();
            }
        }

        int start = trimmed.indexOf('{');
        int end = trimmed.lastIndexOf('}');
        if (start < 0 || end <= start) {
            throw new IllegalStateException("Claude response did not include JSON content.");
        }

        return trimmed.substring(start, end + 1);
    }
}
