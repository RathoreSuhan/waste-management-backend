package com.cleanbharat.wastemanagement.dto;

import lombok.*;

import java.time.LocalDateTime;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CleanupAssignmentResponse {

    // Assignment ID
    private Long assignmentId;

    // Original garbage report ID
    private Long reportId;

    // Garbage report title
    private String reportTitle;

    // Garbage report description
    private String reportDescription;

    // Before-cleanup image uploaded by citizen
    private String beforeImageUrl;

    // After-cleanup image uploaded by cleaner
    private String afterImageUrl;

    // Garbage report address
    private String address;

    // City where cleanup is required
    private String city;

    /**
     * Coordinates of the original citizen report.
     *
     * Sent so the cleaner app can measure the distance to the site before
     * an upload is attempted. The same distance is re-checked on the server,
     * so these values are a convenience for the user, not the safeguard.
     */
    private Double reportLatitude;

    private Double reportLongitude;

    // Current assignment status
    private String assignmentStatus;

    // Current garbage report status
    private String reportStatus;

    // Name of assigned cleaner
    private String cleanerName;

    /**
     * Cleaner identity shown to the reviewing municipal officer.
     *
     * Needed on the municipal dashboard so the officer knows exactly who was
     * authorised, whether they are an individual or an organisation, and how to
     * reach them. Null while a site is still open for proposals.
     */
    private Long cleanerId;

    private String cleanerEmail;

    private String cleanerType; // INDIVIDUAL / NGO / CONTRACTOR ... as recorded on the account

    private String cleanerOrganization; // blank for individual cleaners

    // Municipal Corporation responsible
    private String municipalCorporation;

    // AI verification result
    private Boolean aiVerified;

    // AI confidence score
    private Double aiConfidence;

    // AI remarks
    private String aiRemarks;

    // Assignment claim time
    private LocalDateTime claimedAt;

    // Cleanup start time
    private LocalDateTime startedAt;

    /**
     * Start-of-work location evidence (Phase 16).
     *
     * Captured when the authorised cleaner pressed START CLEANUP and verified
     * against the report coordinates on the server, so it proves the work
     * actually began at the site.
     */
    private Double startLatitude;

    private Double startLongitude;

    /**
     * Server-measured distance between the start position and the report.
     *
     * This is the recorded GPS verification result the municipal officer reads
     * against the 50 m proximity rule; null for legacy rows started before
     * location evidence was captured.
     */
    private Double startDistanceMeters;

    // Cleanup completion time
    private LocalDateTime completedAt;

    /**
     * Number of optional activity entries recorded for this cleanup.
     *
     * Zero is perfectly valid: the work diary is optional and a short one-day
     * cleanup normally has no entries at all.
     */
    private Integer activityLogCount;

    // How many cleaners bid for this site (municipal proposal queue)
    private Long proposalCount;

    // Garbage report creation time
    private LocalDateTime reportCreatedAt;
}