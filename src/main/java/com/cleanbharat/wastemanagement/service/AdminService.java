package com.cleanbharat.wastemanagement.service;

import com.cleanbharat.wastemanagement.dto.admin.DashboardResponse;
import com.cleanbharat.wastemanagement.dto.admin.UserDetailsResponse;
import com.cleanbharat.wastemanagement.dto.admin.UserSummaryResponse;
import com.cleanbharat.wastemanagement.dto.ReportResponse;
import com.cleanbharat.wastemanagement.dto.common.PageResponse;
import com.cleanbharat.wastemanagement.enums.ReportStatus;
import com.cleanbharat.wastemanagement.dto.SuccessResponse;
import com.cleanbharat.wastemanagement.enums.Role;

/**
 * Service responsible for all
 * administrative operations.
 *
 * Phase 12 starts with:
 * - Dashboard
 * - User Management
 */
public interface AdminService {

    /**
     * Returns overall system statistics for the Admin Dashboard.
     */
    DashboardResponse getDashboard();

    /*
      ========================================================================
      Paged registers
      ========================================================================

      Both admin registers - accounts and reports - grow with the platform
      and used to be returned whole. They are now one page at a time.

      Pagination parameters follow the same convention on every one of them:
      zero-based `page`, clamped `size`, whitelisted `sortBy` and
      `direction`, and a `PageResponse` wrapper carrying the totals.
    */

    /**
     * One page of every registered user, optionally narrowed to one role.
     */
    PageResponse<UserSummaryResponse> getUsers(
            Role role,
            int page,
            int size,
            String sortBy,
            String direction
    );

    /**
     * One page of users matching a name or email fragment, optionally
     * within one role.
     */
    PageResponse<UserSummaryResponse> searchUsers(
            String keyword,
            Role role,
            int page,
            int size,
            String sortBy,
            String direction
    );

    /**
     * Returns detailed information about a single user.
     */
    UserDetailsResponse getUserDetails(Long userId);

    /**
     * Deletes a citizen or cleaner.
     * Admin users cannot be deleted through the application.
     */
    SuccessResponse deleteUser(Long userId);

    /**
     * Promotes a citizen to Admin.
     */
    SuccessResponse promoteCitizenToAdmin(Long userId);

    /**
     * One page of reports matching a keyword against title, district, city,
     * state, pincode or address.
     */
    PageResponse<ReportResponse> searchReports(
            String keyword,
            int page,
            int size,
            String sortBy,
            String direction
    );

    /**
     * One page of reports matching any combination of status and the
     * geographical State + District pair.
     *
     * The district is the administrative area the location model filters by;
     * it is read together with the state so identically named districts in
     * different states never mix. Input is normalised the way every
     * canonical location is stored, and a legacy report whose district was
     * never reconciled matches no district filter.
     */
    PageResponse<ReportResponse> filterReports(
            ReportStatus status,
            String district,
            String state,
            int page,
            int size,
            String sortBy,
            String direction
    );

    /**
     * Deletes a garbage report along with all dependent data.
     */
    SuccessResponse deleteReport(Long reportId);
}