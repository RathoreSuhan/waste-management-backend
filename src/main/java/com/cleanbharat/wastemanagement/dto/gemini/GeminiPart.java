package com.cleanbharat.wastemanagement.dto.gemini;

import lombok.*;

/**
 * Represents a single part of Gemini content.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GeminiPart {

    // Prompt text sent to Gemini
    private String text;
}