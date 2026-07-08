package com.cleanbharat.wastemanagement.service.ai;

import com.cleanbharat.wastemanagement.dto.ai.AIReportValidationResponse;
import org.springframework.web.multipart.MultipartFile;

/**
 * Validates newly uploaded garbage report images before storing them in Cloudinary or Database.
 */
public interface AIReportValidationService {

    /**
     * Validates uploaded garbage image using Gemini Vision.
     */
    AIReportValidationResponse validateReportImage(MultipartFile image);

}