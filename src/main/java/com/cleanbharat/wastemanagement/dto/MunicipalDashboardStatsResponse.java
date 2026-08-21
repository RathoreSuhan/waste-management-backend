package com.cleanbharat.wastemanagement.dto;

import lombok.*;

/**
 * Overview tiles for the Municipal Corporation dashboard.
 *
 * Every figure is scoped to the signed-in corporation: a municipal body
 * is not a platform administrator and never sees national totals.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MunicipalDashboardStatsResponse {

    // Identity of the corporation behind the current login
    private String corporationName;

    // One corporation per city, so the city alone describes its jurisdiction
    private String city;

    // Every cleanup site that falls under this corporation
    private long relevantReports;

    // Sites where cleaners have bid and a proposal decision is pending
    private long pendingProposals;

    // Authorised work: ASSIGNED + IN_PROGRESS + REWORK_REQUIRED
    private long activeCleanups;

    // Evidence submitted and waiting for the officer's final sign-off
    private long completionReviews;

    // Cleanups this corporation has already approved as finished
    private long completedCleanups;
}