package com.cleanbharat.wastemanagement.service;

import com.cleanbharat.wastemanagement.client.GeminiFeignClient;
import com.cleanbharat.wastemanagement.config.GeminiConfig;
import com.cleanbharat.wastemanagement.dto.AIValidationResponse;
import com.cleanbharat.wastemanagement.dto.gemini.*;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class GeminiValidationServiceImpl implements AIValidationService {

    // Calls Gemini API
    private final GeminiFeignClient geminiFeignClient;

    // Gemini configuration
    private final GeminiConfig geminiConfig;

    // Converts JSON String -> Java Object
    private final ObjectMapper objectMapper;

    @Override
    public AIValidationResponse validateImages(String beforeImageUrl, String afterImageUrl){

        // Prompt sent to Gemini
        String prompt = """
                Compare the following BEFORE and AFTER cleanup images.

                BEFORE IMAGE:
                %s

                AFTER IMAGE:
                %s

                Determine:

                1. Are both images from the same location?
                2. Has garbage actually been removed?

                Return ONLY valid JSON.

                {
                  "sameLocation": true,
                  "garbageRemoved": true,
                  "confidence": 0.96,
                  "remarks": "Garbage removed successfully."
                }

                Do not return markdown.
                Do not return explanation.
                Do not wrap JSON inside ``` blocks.
                """.formatted(beforeImageUrl, afterImageUrl);

        // Prompt part
        GeminiPart part = GeminiPart.builder()
                .text(prompt)
                .build();

        // Content
        GeminiContent content = GeminiContent.builder()
                .parts(List.of(part))
                .build();

        // Root request
        GeminiRequest request = GeminiRequest.builder()
                .contents(List.of(content))
                .build();

        // Gemini API call
        GeminiResponse response =
                geminiFeignClient.validateImages(
                        geminiConfig.getApiKey(),
                        request
                );

        try {
            // Extract Gemini generated text
            // Raw response from Gemini
            String json = response.getCandidates()
                            .getFirst()
                            .getContent()
                            .getParts()
                            .getFirst()
                            .getText();

            // Remove Markdown formatting if present
            json = json.replace("```json", "")
                    .replace("```", "")
                    .trim();

            // Convert JSON String into Java DTO
            return objectMapper.readValue(json, AIValidationResponse.class);

        } catch (Exception ex) {

            // Any parsing/API failure
            throw new RuntimeException("Failed to parse Gemini response.", ex);
        }
    }
}