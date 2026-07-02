package com.cleanbharat.wastemanagement.dto.gemini;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

/**
 * Represents one content part sent to Gemini.

 * A part can contain:
 * 1. Text
 * 2. Inline image
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class GeminiPart {

    // Prompt text
    private String text;

    // Inline image data
    @JsonProperty("inline_data")
    private GeminiInlineData inlineData;
}