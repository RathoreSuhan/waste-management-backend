package com.cleanbharat.wastemanagement.enums;

import com.fasterxml.jackson.annotation.JsonCreator;

/**
 * Reason why an uploaded report image was rejected by AI validation.
 *
 * Two booleans (garbageDetected / validReportImage) cannot express WHY an
 * image failed, so the AI returns one of these codes alongside its remarks.
 * Each value carries the guidance shown to the citizen, which keeps the
 * user-facing wording in one place instead of scattered across the service.
 */
public enum ImageRejectionReason {

    /**
     * The photograph simply has no garbage in it.
     */
    NO_GARBAGE(
            "No garbage could be identified in the uploaded photograph. "
                    + "Please upload a photograph that clearly shows the waste."
    ),

    /**
     * Garbage is present but far too small to justify a municipal cleanup.
     */
    INSIGNIFICANT_GARBAGE(
            "Only a very small amount of litter was detected, which does not "
                    + "require a municipal cleanup. Please capture a wider view "
                    + "if the affected area is larger than it appears."
    ),

    /**
     * Cartoons, illustrations, renders, screenshots and AI generated images.
     */
    NOT_REAL_IMAGE(
            "Reports must be filed with a real photograph of the location. "
                    + "Drawings, cartoons, screenshots and AI generated images "
                    + "cannot be accepted."
    ),

    /**
     * Real photograph, but blurred, too dark or otherwise unusable.
     */
    POOR_QUALITY(
            "The photograph is not clear enough to verify. Please upload a "
                    + "sharper, well-lit photograph of the same location."
    ),

    /**
     * A real, clear photograph of something that is not a waste site.
     */
    IRRELEVANT_SUBJECT(
            "The photograph does not appear to show a waste site. Please upload "
                    + "a photograph of the garbage you wish to report."
    ),

    /**
     * AI could not reach a confident conclusion either way.
     */
    UNCERTAIN(
            "The photograph could not be verified with sufficient confidence. "
                    + "Please upload a clearer photograph taken closer to the waste."
    );

    // Guidance shown to the citizen for this rejection reason
    private final String guidance;

    ImageRejectionReason(String guidance) {
        this.guidance = guidance;
    }

    public String getGuidance() {
        return guidance;
    }

    /**
     * Lenient deserialisation.
     *
     * Gemini occasionally invents a code that is not in this enum. Failing the
     * whole parse for that would replace a precise message with a generic one,
     * so anything unrecognised is treated as UNCERTAIN instead.
     */
    @JsonCreator
    public static ImageRejectionReason fromValue(String value) {

        if (value == null || value.isBlank()) {
            return null;
        }

        try {
            return ImageRejectionReason.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException ex) {
            return UNCERTAIN;
        }
    }
}
