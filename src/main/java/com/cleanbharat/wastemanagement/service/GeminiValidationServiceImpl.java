package com.cleanbharat.wastemanagement.service;

import com.cleanbharat.wastemanagement.client.GeminiFeignClient;
import com.cleanbharat.wastemanagement.config.GeminiConfig;
import com.cleanbharat.wastemanagement.dto.AIValidationResponse;
import com.cleanbharat.wastemanagement.dto.gemini.*;
import com.cleanbharat.wastemanagement.exception.AIServiceUnavailableException;
import com.cleanbharat.wastemanagement.util.ImageUtils;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import lombok.extern.slf4j.Slf4j;
import java.util.Base64;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class GeminiValidationServiceImpl implements AIValidationService {

    // Calls Gemini API
    private final GeminiFeignClient geminiFeignClient;

    // Gemini configuration
    private final GeminiConfig geminiConfig;

    // Converts JSON String -> Java Object
    private final ObjectMapper objectMapper;

    // Downloads Cloudinary images
    private final ImageDownloadService imageDownloadService;

    @Override
    public AIValidationResponse validateImages(String beforeImageUrl, String afterImageUrl){
        /*
         * Download BEFORE image
         * from Cloudinary.
         */
        byte[] beforeImage = imageDownloadService.downloadImage(beforeImageUrl);

        /*
         * Download AFTER image
         * from Cloudinary.
         */
        byte[] afterImage = imageDownloadService.downloadImage(afterImageUrl);

        /*
         * Convert images to Base64.
         *
         * Gemini Vision prefers
         * inline images to remote URLs.
         */
        String beforeBase64 = Base64.getEncoder().encodeToString(beforeImage);

        String afterBase64 = Base64.getEncoder().encodeToString(afterImage);

        // Detect BEFORE image MIME type
        String beforeMimeType = ImageUtils.detectMimeType(beforeImage);

        // Detect AFTER image MIME type
        String afterMimeType = ImageUtils.detectMimeType(afterImage);

        // Only allow formats supported by our application
        if (!beforeMimeType.equals("image/jpeg")
                && !beforeMimeType.equals("image/png")
                && !beforeMimeType.equals("image/webp")) {

            log.error("Unsupported BEFORE image format: {}", beforeMimeType);
            throw new RuntimeException("Unsupported BEFORE image format.");
        }

        if (!afterMimeType.equals("image/jpeg")
                && !afterMimeType.equals("image/png")
                && !afterMimeType.equals("image/webp")) {

            log.error("Unsupported AFTER image format: {}", afterMimeType);
            throw new RuntimeException("Unsupported AFTER image format.");
        }

        /*
         * AI instructions.
         */
        String prompt = """
            You are an environmental inspection AI.

            You will receive TWO images.

            Image 1 is BEFORE cleanup.

            Image 2 is AFTER cleanup.

            Carefully compare BOTH images.

            Determine:

            1. Are these exactly the same location?

            2. Is the camera angle reasonably similar?

            3. Has garbage actually been removed?

            Reject if ANY of these occur:

            - Different location
            - Different surroundings
            - Different garbage
            - Random garbage image
            - Fake cleanup
            - Blurry image
            - Image unrelated to before image
            - Camera angle extremely different
            - or any other random image that not related to the Before cleanup image
            - and at last if the after cleanup image is made of AI than also reject it

            If uncertain:

            sameLocation=false

            garbageRemoved=false

            confidence below 0.50

            Return ONLY JSON.

            {
              "sameLocation": true,
              "garbageRemoved": true,
              "confidence": 0.96,
              "remarks": "Garbage removed successfully."
            }

            Never guess.

            Never explain.

            Never return markdown.
            """;

        /*
         * Text prompt.
         */
        GeminiPart promptPart = GeminiPart.builder()
                                           .text(prompt)
                                           .build();

        /*
         * BEFORE image.
         */
        GeminiPart beforeImagePart = GeminiPart.builder()
                .inlineData(
                        GeminiInlineData.builder()
                                .mimeType(beforeMimeType)   // Auto-detected MIME type
                                .data(beforeBase64)         // Base64 image
                                .build()
                )
                .build();

        /*
         * AFTER image.
         */
        GeminiPart afterImagePart = GeminiPart.builder()
                        .inlineData(
                                GeminiInlineData.builder()
                                        .mimeType(afterMimeType)
                                        .data(afterBase64)
                                        .build()
                        )
                        .build();

        /*
         * Entire Gemini request.
         */
        GeminiRequest request = GeminiRequest.builder()
                        .contents(
                                List.of(
                                        GeminiContent.builder()
                                                .parts(
                                                        List.of(
                                                                promptPart,
                                                                beforeImagePart,
                                                                afterImagePart
                                                        )
                                                )
                                                .build()
                                )
                        )
                        .build();

        // Call Gemini with retry mechanism
        GeminiResponse response = callGeminiWithRetry(request);

        try {

            // Safely extract JSON returned by Gemini
            String json = extractJsonResponse(response);

            // Convert JSON into DTO
            AIValidationResponse aiResponse = objectMapper.readValue(json, AIValidationResponse.class);

            // Validate mandatory fields
            validateAiResponse(aiResponse);

            // Return validated response
            return aiResponse;

        } catch (Exception ex) {
            throw new RuntimeException("Failed to parse Gemini response.", ex);
        }
    }

    /**
     * Validates Gemini response before parsing.
     */
    private String extractJsonResponse(GeminiResponse response) {

        // Response must not be null
        if (response == null) {
            throw new RuntimeException("Gemini returned no response.");
        }

        // Candidate list must exist
        if (response.getCandidates() == null || response.getCandidates().isEmpty()) {
            throw new RuntimeException("Gemini returned no candidates.");
        }

        // Content must exist
        GeminiContentResponse content = response.getCandidates()
                        .getFirst()
                        .getContent();

        if (content == null || content.getParts() == null || content.getParts().isEmpty()) {
            throw new RuntimeException("Gemini returned empty content.");
        }

        // AI generated text
        String json = content.getParts()
                        .getFirst()
                        .getText();

        if (json == null || json.isBlank()) {
            throw new RuntimeException("Gemini returned an empty response.");
        }

        // Remove Markdown if present
        return json.replace("```json", "")
                .replace("```", "")
                .trim();
    }

    /**
     * Ensures the AI response contains
     * all required fields.
     */
    private void validateAiResponse(AIValidationResponse response) {

        if (response.getSameLocation() == null) {
            throw new RuntimeException("AI response missing sameLocation.");
        }

        if (response.getGarbageRemoved() == null) {
            throw new RuntimeException("AI response missing garbageRemoved.");
        }

        if (response.getConfidence() == null) {
            throw new RuntimeException("AI response missing confidence.");
        }

        if (response.getRemarks() == null || response.getRemarks().isBlank()) {
            throw new RuntimeException("AI response missing remarks.");
        }
    }

    /**
     * Calls Gemini API with retry support.
     */
    private GeminiResponse callGeminiWithRetry(GeminiRequest request) {

        // Maximum retry attempts
        final int maxAttempts = 3;

        Exception lastException = null;

        // Retry loop
        for (int attempt = 1; attempt <= maxAttempts; attempt++) {

            try {

                // Call Gemini
                return geminiFeignClient.validateImages(geminiConfig.getApiKey(), request);
            } catch (Exception ex) {

                // Remember latest exception
                lastException = ex;

                log.warn("Gemini API attempt {} failed.", attempt, ex);

                // No sleep after last attempt
                if (attempt < maxAttempts) {

                    try {

                        // Small delay before retry
                        Thread.sleep(1000);

                    } catch (InterruptedException interruptedException) {
                        Thread.currentThread().interrupt();
                    }
                }
            }
        }

        // All retries failed
        throw new AIServiceUnavailableException("AI verification is temporarily unavailable. Please try again in a few minutes.", lastException);
    }
}