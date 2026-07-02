package com.cleanbharat.wastemanagement.dto.gemini;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.*;

/**
 * Represents an inline image sent to Gemini.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GeminiInlineData {

    // Image MIME type
    @JsonProperty("mime_type")
    private String mimeType;

    // Base64 image
    @JsonProperty("data")
    private String data;
}