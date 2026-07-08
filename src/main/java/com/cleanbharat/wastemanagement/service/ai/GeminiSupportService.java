package com.cleanbharat.wastemanagement.service.ai;

import com.cleanbharat.wastemanagement.dto.gemini.GeminiRequest;
import com.cleanbharat.wastemanagement.dto.gemini.GeminiResponse;

/**
 * Shared support service for all Gemini AI operations.
 * Contains reusable Gemini communication logic without any business-specific validation.
 */
public interface GeminiSupportService {

    /**
     * Executes Gemini request with retry support.
     */
    GeminiResponse executeRequest(GeminiRequest request);

    /**
     * Extracts JSON text returned by Gemini.
     */
    String extractJsonResponse(GeminiResponse response);

    /**
     * Converts JSON into requested DTO.
     */
    <T> T parseResponse(String json, Class<T> responseType);

}