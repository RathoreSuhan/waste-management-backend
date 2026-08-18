package com.cleanbharat.wastemanagement.exception;

import lombok.Getter;

/**
 * Raised when a cleaner tries to upload cleanup proof from somewhere other
 * than the reported site.
 *
 * Cleanup proof is only meaningful if it was taken where the waste was
 * reported, so the distance between the cleaner's device and the citizen's
 * coordinates is checked before the photograph is stored or sent for AI
 * verification.
 *
 * The measured distance and the permitted radius are carried on the exception
 * as well as in the message, so a caller can act on the numbers instead of
 * parsing the text.
 */
@Getter
public class CleanerTooFarFromSiteException extends RuntimeException {

    // How far the cleaner actually was from the reported location, in metres
    private final double distanceMeters;

    // The radius within which proof is accepted, in metres
    private final double allowedRadiusMeters;

    public CleanerTooFarFromSiteException(
            String message,
            double distanceMeters,
            double allowedRadiusMeters
    ) {
        super(message);
        this.distanceMeters = distanceMeters;
        this.allowedRadiusMeters = allowedRadiusMeters;
    }
}