package com.cleanbharat.wastemanagement.dto.gemini;

import lombok.*;

import java.util.List;

/**
 * Content returned by Gemini.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GeminiContentResponse {

    // AI generated response parts
    private List<GeminiPart> parts;
}