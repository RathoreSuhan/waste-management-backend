package com.cleanbharat.wastemanagement.service.ai;

import com.cleanbharat.wastemanagement.dto.ai.AIReportValidationResponse;
import com.cleanbharat.wastemanagement.enums.GarbageSeverity;
import com.cleanbharat.wastemanagement.exception.InvalidReportImageException;
import com.cleanbharat.wastemanagement.util.ImageUtil;
import com.cleanbharat.wastemanagement.dto.gemini.GeminiContent;
import com.cleanbharat.wastemanagement.dto.gemini.GeminiInlineData;
import com.cleanbharat.wastemanagement.dto.gemini.GeminiPart;
import com.cleanbharat.wastemanagement.dto.gemini.GeminiRequest;
import com.cleanbharat.wastemanagement.dto.gemini.GeminiResponse;

import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@Service
@RequiredArgsConstructor
public class AIReportValidationServiceImpl implements AIReportValidationService {

    // Shared Gemini helper service
    private final GeminiSupportService geminiSupportService;

    // Minimum AI confidence configured in application.properties
    @Value("${report.ai.minimum-confidence}")
    private Double minimumConfidence;

    @Override
    public AIReportValidationResponse validateReportImage(MultipartFile image) {

        try {

            /*
             * Read uploaded image bytes.
             * Image is NOT uploaded to Cloudinary yet.
             */
            byte[] imageBytes = image.getBytes();

            /*
             * Detect image MIME type.
             */
            String mimeType = ImageUtil.detectMimeType(imageBytes);

            /*
             * Validate supported image formats.
             */
            if (!ImageUtil.isSupportedMimeType(mimeType)) {

                log.error("Unsupported report image format : {}", mimeType);

                throw new InvalidReportImageException(
                        "Unsupported image format. "
                                + "Only JPEG, PNG and WEBP images are supported."
                );
            }

            /*
             * Convert image into Base64.
             * Gemini Vision accepts inline Base64 images.
             */
            String base64Image = ImageUtil.convertToBase64(imageBytes);

            /*
             * AI instructions.
             *
             * Validate whether this image is suitable
             * for creating a municipal garbage report.
             */
            String prompt = """
                You are an AI assistant working for a Smart Waste Management System.
        
                You will receive ONE uploaded image.
        
                Carefully inspect the image.
        
                Your task is to determine whether the image is suitable
                for creating a municipal garbage report.
        
                A VALID report image must satisfy ALL of the following:
        
                1. Garbage must be clearly visible.
        
                2. Garbage should be meaningful enough to require municipal cleaning.
        
                3. The image must be real.
        
                4. The image must be reasonably clear.
        
                Reject the image if ANY of the following is true:
        
                - No garbage is present.
                - Garbage is barely visible.
                - Garbage is too small or insignificant
                  (for example a single wrapper,
                  a single plastic bottle,
                  a single paper piece,
                  or similar trivial litter).
        
                - The image mainly contains:
                    * People
                    * Animals
                    * Vehicles
                    * Buildings
                    * Roads without garbage
                    * Nature
                    * Indoor scenes
                    * Random objects
        
                - Image is blurry.
        
                - Image is extremely dark.
        
                - Image quality is too poor.
        
                - Image appears AI generated.
        
                - Unable to confidently determine the contents.
        
                Estimate cleanup severity using these rules:
        
                LOW
                Small amount of garbage.
                Does not require municipal cleanup.
        
                MEDIUM
                Noticeable garbage accumulation.
                Municipal cleanup recommended.
        
                HIGH
                Large garbage accumulation.
                Immediate municipal cleanup required.
        
                Respond ONLY in JSON.
        
                {
                  "garbageDetected": true,
                  "validReportImage": true,
                  "confidence": 0.95,
                  "garbageCategory": "Plastic Garbage",
                  "severity": "HIGH",
                  "remarks": "Plastic garbage is scattered along the roadside."
                }
        
                Never return markdown.
        
                Never explain outside JSON.
        
                Never guess.
        
                If garbage is NOT detected:
                
                garbageDetected = false
                
                validReportImage = false
                
                garbageCategory = null
                
                severity = null
                
                Return the confidence of your classification.
                
                Example:
                
                {
                  "garbageDetected": false,
                  "validReportImage": false,
                  "confidence": 0.95,
                  "garbageCategory": null,
                  "severity": null,
                  "remarks": "No garbage is visible in the image."
                }
                
                If uncertain:
                
                garbageDetected = false
                
                validReportImage = false
                
                confidence below 0.50
                
                garbageCategory = null
                
                severity = null
                """;

            /*
             * Prompt text.
             */
            GeminiPart promptPart = GeminiPart.builder()
                    .text(prompt)
                    .build();

            /*
             * Uploaded image.
             */
            GeminiPart imagePart = GeminiPart.builder()
                    .inlineData(
                            GeminiInlineData.builder()
                                    .mimeType(mimeType)
                                    .data(base64Image)
                                    .build()
                    )
                    .build();

            /*
             * Complete Gemini request.
             */
            GeminiRequest request = GeminiRequest.builder()
                    .contents(
                            List.of(
                                    GeminiContent.builder()
                                            .parts(
                                                    List.of(
                                                            promptPart,
                                                            imagePart
                                                    )
                                            )
                                            .build()
                            )
                    )
                    .build();

            /*
             * Execute Gemini request.
             */
            GeminiResponse response = geminiSupportService.executeRequest(request);

            try {

                // Extract JSON returned by Gemini
                String json = geminiSupportService.extractJsonResponse(response);

                log.info("Gemini Raw JSON:\n{}", json);

                // Convert JSON into DTO
                AIReportValidationResponse aiResponse =
                        geminiSupportService.parseResponse(
                                json,
                                AIReportValidationResponse.class
                        );

                // Validate mandatory AI fields
                validateAiResponse(aiResponse);

                // Validate report using business rules
                validateBusinessRules(aiResponse);

                return aiResponse;

            } catch (InvalidReportImageException ex) {

                throw ex;

            } catch (Exception ex) {

                log.error("Failed to parse AI report validation response.", ex);

                throw new InvalidReportImageException(
                        "Unable to validate uploaded report image."
                );
            }

        } catch (InvalidReportImageException ex) {

            throw ex;

        } catch (Exception ex) {
            log.error("Failed to read uploaded report image.", ex);
            throw new InvalidReportImageException("Unable to process uploaded image.");
        }
    }


    /**
     * Validates mandatory fields returned by Gemini.
     */
    private void validateAiResponse(AIReportValidationResponse response) {

        if (response.getGarbageDetected() == null) {
            throw new InvalidReportImageException(
                    "AI response missing garbageDetected."
            );
        }

        if (response.getValidReportImage() == null) {
            throw new InvalidReportImageException(
                    "AI response missing validReportImage."
            );
        }

        if (response.getConfidence() == null) {
            throw new InvalidReportImageException(
                    "AI response missing confidence."
            );
        }

        /*
         * Severity and garbage category are required
         * only when garbage is actually detected.
         */
        if (response.getGarbageDetected()) {
            if (response.getGarbageCategory() == null || response.getGarbageCategory().isBlank()) {
                throw new InvalidReportImageException(
                        "AI response missing garbageCategory."
                );
            }

            if (response.getSeverity() == null) {
                throw new InvalidReportImageException(
                        "AI response missing severity."
                );
            }
        }

        if (response.getRemarks() == null || response.getRemarks().isBlank()) {
            throw new InvalidReportImageException(
                    "AI response missing remarks."
            );
        }
    }

    /**
     * Applies business validation rules before accepting the report.
     */
    private void validateBusinessRules(AIReportValidationResponse response) {

        // No garbage detected
        if (!response.getGarbageDetected()) {
            throw new InvalidReportImageException(
                    "The uploaded image does not contain garbage."
            );
        }

        // AI rejected the image
        if (!response.getValidReportImage()) {
            throw new InvalidReportImageException(
                    response.getRemarks()
            );
        }

        // Confidence below configured threshold
        if (response.getConfidence() < minimumConfidence) {
            throw new InvalidReportImageException(
                    "Unable to validate the uploaded image with sufficient confidence. Please upload a clearer image."
            );
        }

        // Garbage is too insignificant
        if (response.getSeverity() == GarbageSeverity.LOW) {
            throw new InvalidReportImageException(
                    "Very little garbage was detected. Please upload another image from a better angle or capture a larger affected area."
            );
        }
    }

}