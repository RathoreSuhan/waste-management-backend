package com.cleanbharat.wastemanagement.dto.gemini;

import lombok.*;

/**
 * One AI candidate returned by Gemini.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GeminiCandidate {

    // AI generated content
    private GeminiContentResponse content;
}