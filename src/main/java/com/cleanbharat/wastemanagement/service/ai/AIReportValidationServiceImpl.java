package com.cleanbharat.wastemanagement.service.ai;

import com.cleanbharat.wastemanagement.dto.ai.AIReportValidationResponse;
import com.cleanbharat.wastemanagement.enums.GarbageSeverity;
import com.cleanbharat.wastemanagement.enums.ImageRejectionReason;
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

    // Width of GarbageReport.garbageCategory, the column this free text is stored in
    private static final int MAX_GARBAGE_CATEGORY_LENGTH = 100;

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
        
                - The image is NOT a real photograph.
                  This includes:
                    * Cartoons
                    * Illustrations and drawings
                    * Paintings and sketches
                    * 3D renders
                    * Computer graphics
                    * Screenshots
                    * AI generated images
        
                - Unable to confidently determine the contents.
        
                You must also return a rejectionReason.
        
                Use EXACTLY one of these values:
        
                NO_GARBAGE
                A real photograph containing no garbage at all.
        
                INSIGNIFICANT_GARBAGE
                A real photograph with only trivial litter.
        
                NOT_REAL_IMAGE
                Cartoon, illustration, drawing, painting, render,
                screenshot or AI generated image.
                Use this even when the drawing depicts garbage.
        
                POOR_QUALITY
                A real photograph that is blurry, too dark or unusable.
        
                IRRELEVANT_SUBJECT
                A real, clear photograph whose subject is not a waste site.
        
                UNCERTAIN
                You cannot confidently determine the contents.
        
                Set rejectionReason to null when the image is acceptable.
        
                When more than one applies, choose in this order:
                NOT_REAL_IMAGE, POOR_QUALITY, IRRELEVANT_SUBJECT,
                NO_GARBAGE, INSIGNIFICANT_GARBAGE, UNCERTAIN.
        
                The remarks field must state plainly what you actually see
                in the image, in one sentence.
        
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
                  "rejectionReason": null,
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
                  "rejectionReason": "NO_GARBAGE",
                  "remarks": "No garbage is visible in the image."
                }
                
                Example of a cartoon or illustration:
                
                {
                  "garbageDetected": false,
                  "validReportImage": false,
                  "confidence": 0.95,
                  "garbageCategory": null,
                  "severity": null,
                  "rejectionReason": "NOT_REAL_IMAGE",
                  "remarks": "The image is an illustration, not a real photograph."
                }
                
                If uncertain:
                
                garbageDetected = false
                
                validReportImage = false
                
                confidence below 0.50
                
                garbageCategory = null
                
                severity = null
                
                rejectionReason = "UNCERTAIN"
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

            /*
             * The category is free text written by the model, and it is the only
             * value on a report that no @Size governs. It is trimmed to the column
             * width here, because a longer answer would otherwise fail the whole
             * insert at save time - after the photograph had already been accepted
             * and uploaded.
             */
            String garbageCategory = response.getGarbageCategory().trim();

            if (garbageCategory.length() > MAX_GARBAGE_CATEGORY_LENGTH) {

                log.warn("AI returned a {} character garbageCategory, keeping the first {}.",
                        garbageCategory.length(), MAX_GARBAGE_CATEGORY_LENGTH);

                garbageCategory = garbageCategory.substring(0, MAX_GARBAGE_CATEGORY_LENGTH);
            }

            response.setGarbageCategory(garbageCategory);

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

        /*
         * A rejected image should always carry a reason, but a missing one is
         * not worth failing the whole validation over. Defaulting to UNCERTAIN
         * keeps the AI's own remarks reaching the citizen, which is far more
         * useful than a generic "unable to validate" message.
         */
        boolean rejected = !response.getGarbageDetected()
                || !response.getValidReportImage();

        if (rejected && response.getRejectionReason() == null) {

            log.warn(
                    "AI rejected the image without a rejectionReason. Remarks: {}",
                    response.getRemarks()
            );

            response.setRejectionReason(ImageRejectionReason.UNCERTAIN);
        }
    }

    /**
     * Applies business validation rules before accepting the report.
     *
     * Ordered from the most specific reason to the least, because the AI sets
     * several flags at once for a single underlying problem. A cartoon, for
     * example, comes back with garbageDetected=false AND validReportImage=false,
     * so checking the generic "no garbage" case first would hide the fact that
     * the real problem was the image not being a photograph.
     *
     * Every rejection carries the AI remarks so the citizen is told what the
     * AI actually saw, rather than a fixed sentence.
     */
    private void validateBusinessRules(AIReportValidationResponse response) {

        /*
         * Low confidence is handled first.
         *
         * When the AI is unsure, its other flags are not trustworthy enough to
         * base a specific explanation on.
         */
        if (response.getConfidence() < minimumConfidence) {

            log.info(
                    "Report image rejected due to low AI confidence: {} (minimum {})",
                    response.getConfidence(),
                    minimumConfidence
            );

            throw rejection(response, ImageRejectionReason.UNCERTAIN);
        }

        /*
         * Image rejected by the AI, for whichever reason it reported.
         *
         * Both flags are covered together so the AI's own rejectionReason
         * decides the wording instead of the order of these checks.
         */
        if (!response.getValidReportImage() || !response.getGarbageDetected()) {

            ImageRejectionReason reason = response.getRejectionReason();

            /*
             * Guard against the AI reporting no garbage while leaving the
             * reason as something unrelated to that.
             */
            if (reason == null) {
                reason = ImageRejectionReason.NO_GARBAGE;
            }

            log.info(
                    "Report image rejected by AI. Reason: {}, Remarks: {}",
                    reason,
                    response.getRemarks()
            );

            throw rejection(response, reason);
        }

        // Garbage is present but too insignificant for a municipal cleanup
        if (response.getSeverity() == GarbageSeverity.LOW) {

            log.info(
                    "Report image rejected as garbage severity is LOW. Remarks: {}",
                    response.getRemarks()
            );

            throw rejection(response, ImageRejectionReason.INSIGNIFICANT_GARBAGE);
        }
    }

    /**
     * Builds a rejection carrying the guidance for the reason together with
     * the AI's verbatim remarks and confidence.
     */
    private InvalidReportImageException rejection(
            AIReportValidationResponse response,
            ImageRejectionReason reason
    ) {

        return new InvalidReportImageException(
                reason.getGuidance(),
                reason,
                response.getRemarks(),
                response.getConfidence()
        );
    }

}
