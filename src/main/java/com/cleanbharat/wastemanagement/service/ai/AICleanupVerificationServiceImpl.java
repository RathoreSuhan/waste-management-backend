package com.cleanbharat.wastemanagement.service.ai;

import com.cleanbharat.wastemanagement.dto.ai.AICleanupVerificationResponse;
import com.cleanbharat.wastemanagement.dto.gemini.*;
import com.cleanbharat.wastemanagement.service.ImageDownloadService;
import com.cleanbharat.wastemanagement.util.ImageUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import lombok.extern.slf4j.Slf4j;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class AICleanupVerificationServiceImpl implements AICleanupVerificationService {

    // Shared Gemini support service
    private final GeminiSupportService geminiSupportService;

    // Downloads Cloudinary images
    private final ImageDownloadService imageDownloadService;


    @Override
    public AICleanupVerificationResponse validateImages(String beforeImageUrl, String afterImageUrl){
        /*
         * Download BEFORE image from Cloudinary.
         */
        byte[] beforeImage = imageDownloadService.downloadImage(beforeImageUrl);

        /*
         * Download AFTER image from Cloudinary.
         */
        byte[] afterImage = imageDownloadService.downloadImage(afterImageUrl);

        /*
         * Convert images to Base64.
         *
         * Gemini Vision prefers inline images to remote URLs.
         */
        String beforeBase64 = ImageUtil.convertToBase64(beforeImage);

        String afterBase64 = ImageUtil.convertToBase64(afterImage);


        // Detect BEFORE image MIME type
        String beforeMimeType = ImageUtil.detectMimeType(beforeImage);

        // Detect AFTER image MIME type
        String afterMimeType = ImageUtil.detectMimeType(afterImage);

        // Only allow formats supported by our application
        if (!ImageUtil.isSupportedMimeType(beforeMimeType)) {
            log.error("Unsupported BEFORE image format: {}", beforeMimeType);
            throw new RuntimeException("Unsupported BEFORE image format. We accept image in jpeg/png/webp format only.");
        }

        if (!ImageUtil.isSupportedMimeType(afterMimeType)) {
            log.error("Unsupported AFTER image format: {}", afterMimeType);
            throw new RuntimeException("Unsupported AFTER image format. We accept image in jpeg/png/webp format only.");
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
        GeminiResponse response = geminiSupportService.executeRequest(request);

        try {

            // Safely extract JSON returned by Gemini
            String json = geminiSupportService.extractJsonResponse(response);

            // Convert JSON into DTO
            AICleanupVerificationResponse aiResponse =
                    geminiSupportService.parseResponse(
                            json,
                            AICleanupVerificationResponse.class
                    );

            // Validate mandatory fields
            validateAiResponse(aiResponse);

            // Return validated response
            return aiResponse;

        } catch (Exception ex) {
            throw new RuntimeException("Failed to parse Gemini response.", ex);
        }
    }



    /**
     * Ensures the AI response contains
     * all required fields.
     */
    private void validateAiResponse(AICleanupVerificationResponse response) {

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
}