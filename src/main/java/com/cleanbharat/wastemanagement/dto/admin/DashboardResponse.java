package com.cleanbharat.wastemanagement.dto.admin;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/*
 * Response DTO for the Admin Dashboard.
 *
 * This class aggregates high-level statistics
 * from multiple modules of the application.
 *
 * It is used only for API responses.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DashboardResponse {

    // Total registered users
    private Long totalUsers;

    // Users having ROLE_CITIZEN
    private Long totalCitizens;

    // Users having ROLE_CLEANER
    private Long totalCleaners;

    // Users having ROLE_ADMIN
    private Long totalAdmins;

    // Total garbage reports
    private Long totalReports;

    // Reports waiting for action
    private Long pendingReports;

    // Successfully completed reports
    private Long completedReports;

    // AI verified cleanup assignments
    private Long verifiedCleanups;

    // Total comments across all reports
    private Long totalComments;

    // Total citizen votes
    private Long totalVotes;

    // Name of highest ranked cleaner
    private String topCleaner;
}