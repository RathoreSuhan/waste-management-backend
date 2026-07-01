package com.cleanbharat.wastemanagement.service;

import com.cleanbharat.wastemanagement.dto.AIValidationResponse;

public interface AIValidationService {

    // Compare before & after cleanup images
    AIValidationResponse validateImages(String beforeImageUrl, String afterImageUrl);

}