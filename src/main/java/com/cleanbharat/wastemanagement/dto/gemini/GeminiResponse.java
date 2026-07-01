package com.cleanbharat.wastemanagement.dto.gemini;

import lombok.*;

import java.util.List;

/**
 * Root response from Gemini API.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GeminiResponse {

    // AI generated candidates
    private List<GeminiCandidate> candidates;
}