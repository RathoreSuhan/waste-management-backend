package com.cleanbharat.wastemanagement.service.ai;

import com.cleanbharat.wastemanagement.client.GeminiFeignClient;
import com.cleanbharat.wastemanagement.config.GeminiConfig;
import com.cleanbharat.wastemanagement.dto.gemini.GeminiContentResponse;
import com.cleanbharat.wastemanagement.dto.gemini.GeminiRequest;
import com.cleanbharat.wastemanagement.dto.gemini.GeminiResponse;
import com.cleanbharat.wastemanagement.exception.AIServiceUnavailableException;
import com.fasterxml.jackson.databind.ObjectMapper;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

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
     * Executes Gemini requests while trying a small fallback chain when Google
     * rejects the primary model because it is unavailable or quota-limited.
     */
    @Override
    public GeminiResponse executeRequest(GeminiRequest request) {

        List<String> candidateModels = new ArrayList<>();
        candidateModels.add(geminiConfig.getModel());
        candidateModels.addAll(geminiConfig.getFallbackModelList());

        Exception lastException = null;

        for (int index = 0; index < candidateModels.size(); index++) {
            String candidateModel = candidateModels.get(index);

            try {

                // Execute Gemini API request with the current model.
                return geminiFeignClient.generateContent(

                        // Gemini Model Name
                        candidateModel,

                        // Gemini API Key
                        geminiConfig.getApiKey(),

                        // Gemini Request DTO
                        request
                );

            } catch (Exception ex) {

                lastException = ex;

                if (isQuotaExceeded(ex)) {
                    log.warn("Gemini quota is exhausted for model '{}'.", candidateModel, ex);

                    if (index < candidateModels.size() - 1) {
                        log.warn("Trying fallback model '{}' instead.", candidateModels.get(index + 1));
                        continue;
                    }

                    throw new AIServiceUnavailableException(
                            "Gemini is temporarily rate-limited. Please wait a moment and try again.",
                            ex
                    );
                }

                if (isModelUnavailable(ex) && index < candidateModels.size() - 1) {
                    log.warn("Gemini model '{}' is unavailable. Trying fallback model '{}'.", candidateModel, candidateModels.get(index + 1), ex);
                    continue;
                }

                log.warn("Gemini API call failed for model '{}'.", candidateModel, ex);
                break;
            }
        }

        throw new AIServiceUnavailableException(
                "AI verification is temporarily unavailable. Please try again in a few minutes.",
                lastException
        );
    }

    private boolean isQuotaExceeded(Exception ex) {
        if (ex instanceof FeignException feignException) {
            return feignException.status() == 429;
        }

        return false;
    }

    private boolean isModelUnavailable(Exception ex) {
        if (ex instanceof FeignException feignException) {
            return feignException.status() == 404;
        }

        return false;
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