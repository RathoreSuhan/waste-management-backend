package com.cleanbharat.wastemanagement.dto.ai;

import com.cleanbharat.wastemanagement.enums.GarbageSeverity;
import lombok.*;

/**
 * AI response returned after validating
 * a newly uploaded garbage report image.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AIReportValidationResponse {

    // Whether the uploaded image actually contains garbage
    private Boolean garbageDetected;

    //Whether the reported image is valid or not(maybe blurry image, AI generated image,etc.)
    private Boolean validReportImage;

    // AI confidence score (0.0 - 1.0)
    private Double confidence;

    // Generic garbage description returned by Gemini
    // Example:
    // "Plastic Garbage"
    // "Medical Garbage"
    // "Mixed Garbage"
    private String garbageCategory;

    // Estimated garbage severity
    private GarbageSeverity severity;

    // AI explanation
    private String remarks;
}