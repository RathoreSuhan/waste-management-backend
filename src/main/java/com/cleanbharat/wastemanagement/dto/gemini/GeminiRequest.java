package com.cleanbharat.wastemanagement.dto.gemini;

import lombok.*;

import java.util.List;

/**
 * Root request sent to Gemini API.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class GeminiRequest {

    // Complete conversation sent to Gemini
    private List<GeminiContent> contents;
}