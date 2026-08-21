package com.cleanbharat.wastemanagement.dto;

import jakarta.validation.constraints.*;
import lombok.*;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDate;

/**
 * ============================================================================
 * CreateProposalRequest (Phase 14)
 * ============================================================================
 *
 * What a cleaner sends when offering to clean a reported site.
 *
 * Bound with @ModelAttribute because the inspection photograph travels in
 * the same multipart request as the plan, exactly like report creation.
 * ============================================================================
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreateProposalRequest {

    // Inspection evidence -------------------------------------------------

    /**
     * Optional site photograph. The coordinates below are the mandatory
     * proof of presence; the picture only helps the officer decide.
     */
    private MultipartFile inspectionImage;

    /**
     * Where the cleaner stood while inspecting. Required: the platform
     * checks this against the reported location before accepting.
     */
    @NotNull(message = "Inspection latitude is required")
    @DecimalMin(value = "-90.0", message = "Inspection latitude must be between -90 and 90")
    @DecimalMax(value = "90.0", message = "Inspection latitude must be between -90 and 90")
    private Double inspectionLatitude;

    @NotNull(message = "Inspection longitude is required")
    @DecimalMin(value = "-180.0", message = "Inspection longitude must be between -180 and 180")
    @DecimalMax(value = "180.0", message = "Inspection longitude must be between -180 and 180")
    private Double inspectionLongitude;

    @NotBlank(message = "Site observations are required")
    @Size(min = 20, max = 1000, message = "Site observations must be between 20 and 1000 characters")
    private String siteObservations;

    // Execution plan ------------------------------------------------------

    @NotNull(message = "Estimated duration is required")
    @Min(value = 1, message = "Estimated duration must be at least 1 day")
    @Max(value = 30, message = "Estimated duration cannot exceed 30 days")
    private Integer estimatedDurationDays;

    @NotNull(message = "Manpower count is required")
    @Min(value = 1, message = "At least 1 worker must be deployed")
    @Max(value = 100, message = "Manpower count cannot exceed 100")
    private Integer manpowerCount;

    @NotBlank(message = "Equipment details are required")
    @Size(max = 500, message = "Equipment details cannot exceed 500 characters")
    private String equipment;

    @NotBlank(message = "Cleaning method is required")
    @Size(max = 500, message = "Cleaning method cannot exceed 500 characters")
    private String cleaningMethod;

    @NotBlank(message = "Waste handling plan is required")
    @Size(min = 20, max = 1000, message = "Waste handling plan must be between 20 and 1000 characters")
    private String wasteHandlingPlan;

    @Size(max = 200, message = "Estimated waste volume cannot exceed 200 characters")
    private String estimatedWasteVolume;

    /**
     * ISO date from the form field, e.g. 2026-08-25.
     */
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
    @FutureOrPresent(message = "Proposed start date cannot be in the past")
    private LocalDate proposedStartDate;

    @Size(max = 1000, message = "Remarks cannot exceed 1000 characters")
    private String remarks;
}
