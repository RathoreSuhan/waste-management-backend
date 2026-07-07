package com.cleanbharat.wastemanagement.service;

import com.cleanbharat.wastemanagement.dto.admin.DashboardResponse;
import com.cleanbharat.wastemanagement.dto.admin.UserDetailsResponse;
import com.cleanbharat.wastemanagement.dto.admin.UserSummaryResponse;
import com.cleanbharat.wastemanagement.dto.ReportResponse;
import com.cleanbharat.wastemanagement.enums.ReportStatus;
import com.cleanbharat.wastemanagement.dto.SuccessResponse;
import com.cleanbharat.wastemanagement.enums.Role;

import java.util.List;

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

    /**
     * Returns all users.
     */
    List<UserSummaryResponse> getAllUsers();

    /**
     * Returns users filtered by a specific role.
     */
    List<UserSummaryResponse> getUsersByRole(Role role);

    /**
     * Searches users by name or email.
     * If role is provided, search is limited to that role.
     */
    List<UserSummaryResponse> searchUsers(String keyword, Role role);

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
     * Searches reports using title, city, state or pincode.
     */
    List<ReportResponse> searchReports(String keyword);

    /**
     * Filters reports.
     */
    List<ReportResponse> filterReports(
            ReportStatus status,
            String city,
            String state
    );

    /**
     * Deletes a garbage report along with all dependent data.
     */
    SuccessResponse deleteReport(Long reportId);
}