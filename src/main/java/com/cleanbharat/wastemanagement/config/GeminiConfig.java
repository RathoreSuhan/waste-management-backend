package com.cleanbharat.wastemanagement.config;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

import java.util.Arrays;
import java.util.List;

@Getter
@Configuration
public class GeminiConfig {

    // Gemini API Key
    @Value("${gemini.api.key}")
    private String apiKey;

    // Primary Gemini model name
    @Value("${gemini.model}")
    private String model;

    // Optional fallback model names to try when the primary model is
    // unavailable or rate-limited by Google.
    @Value("${gemini.fallback-models:}")
    private String fallbackModels;

    public List<String> getFallbackModelList() {
        return Arrays.stream(fallbackModels.split(","))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .toList();
    }
}