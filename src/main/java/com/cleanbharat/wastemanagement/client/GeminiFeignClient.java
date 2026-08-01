package com.cleanbharat.wastemanagement.client;

import com.cleanbharat.wastemanagement.dto.gemini.GeminiRequest;
import com.cleanbharat.wastemanagement.dto.gemini.GeminiResponse;
import org.springframework.cloud.openfeign.FeignClient;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;

@FeignClient(
        name = "gemini-client",
        url = "https://generativelanguage.googleapis.com"
)
public interface GeminiFeignClient {

    /**
     * Calls Gemini Vision API.
     */
    @PostMapping("/v1beta/models/{model}:generateContent")
    GeminiResponse generateContent(          // Receive mapped response DTO

             // Gemini Model Name
             @PathVariable("model") String model,

             // Gemini API Key
             @RequestHeader("x-goog-api-key") String apiKey,

             // Gemini Request DTO
             @RequestBody GeminiRequest request
    );

}