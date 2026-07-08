package com.cleanbharat.wastemanagement.service.ai;

import com.cleanbharat.wastemanagement.dto.ai.AICleanupVerificationResponse;

public interface AICleanupVerificationService {

    // Compare before & after cleanup images
    AICleanupVerificationResponse validateImages(String beforeImageUrl, String afterImageUrl);

}