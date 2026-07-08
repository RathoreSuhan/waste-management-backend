package com.cleanbharat.wastemanagement.service.ai;

import com.cleanbharat.wastemanagement.client.GeminiFeignClient;
import com.cleanbharat.wastemanagement.config.GeminiConfig;
import com.cleanbharat.wastemanagement.dto.gemini.GeminiContentResponse;
import com.cleanbharat.wastemanagement.dto.gemini.GeminiRequest;
import com.cleanbharat.wastemanagement.dto.gemini.GeminiResponse;
import com.cleanbharat.wastemanagement.exception.AIServiceUnavailableException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * Shared implementation for communicating
 * with Google Gemini Vision API.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class GeminiSupportServiceImpl implements GeminiSupportService {

    // Gemini REST client
    private final GeminiFeignClient geminiFeignClient;

    // Gemini configuration
    private final GeminiConfig geminiConfig;

    // JSON mapper
    private final ObjectMapper objectMapper;

    /**
     * Executes Gemini request with retry support.
     */
    @Override
    public GeminiResponse executeRequest(GeminiRequest request) {

        final int maxAttempts = 3; // Maximum retry count

        Exception lastException = null;

        for (int attempt = 1; attempt <= maxAttempts; attempt++) {

            try {

                // Execute Gemini API request
                return geminiFeignClient.generateContent(
                        geminiConfig.getApiKey(),
                        request
                );

            } catch (Exception ex) {

                lastException = ex;

                log.warn("Gemini API attempt {} failed.", attempt, ex);

                if (attempt < maxAttempts) {

                    try {

                        // Small delay before next retry
                        Thread.sleep(1000);

                    } catch (InterruptedException interruptedException) {

                        Thread.currentThread().interrupt();
                    }
                }
            }
        }

        throw new AIServiceUnavailableException(
                "AI verification is temporarily unavailable. Please try again in a few minutes.",
                lastException
        );
    }

    /**
     * Extracts JSON returned by Gemini.
     */
    @Override
    public String extractJsonResponse(GeminiResponse response) {

        if (response == null) {
            throw new RuntimeException("Gemini returned no response.");
        }

        if (response.getCandidates() == null || response.getCandidates().isEmpty()) {
            throw new RuntimeException("Gemini returned no candidates.");
        }

        GeminiContentResponse content = response.getCandidates()
                .getFirst()
                .getContent();

        if (content == null
                || content.getParts() == null
                || content.getParts().isEmpty()) {

            throw new RuntimeException("Gemini returned empty content.");
        }

        String json = content.getParts()
                .getFirst()
                .getText();

        if (json == null || json.isBlank()) {
            throw new RuntimeException("Gemini returned an empty response.");
        }

        // Remove Markdown formatting if Gemini returns it
        return json.replace("```json", "")
                .replace("```", "")
                .trim();
    }

    /**
     * Converts JSON into requested DTO.
     */
    @Override
    public <T> T parseResponse(String json, Class<T> responseType) {

        try {

            return objectMapper.readValue(json, responseType);

        } catch (Exception ex) {

            throw new RuntimeException(
                    "Failed to parse Gemini response.",
                    ex
            );
        }
    }

}