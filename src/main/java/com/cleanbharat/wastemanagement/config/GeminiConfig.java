package com.cleanbharat.wastemanagement.config;

import lombok.Getter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;

@Getter
@Configuration
public class GeminiConfig {

    // Gemini API Key
    @Value("${gemini.api.key}")
    private String apiKey;

    // Gemini Model Name
    @Value("${gemini.model}")
    private String model;

}