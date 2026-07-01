package com.cleanbharat.wastemanagement.dto.gemini;

import lombok.*;

import java.util.List;

/**
 * Represents the "contents" object in Gemini request.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GeminiContent {

    // List of prompt parts
    private List<GeminiPart> parts;
}