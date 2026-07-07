package com.cleanbharat.wastemanagement.controller;

import com.cleanbharat.wastemanagement.dto.ReportResponse;
import com.cleanbharat.wastemanagement.enums.ReportStatus;
import com.cleanbharat.wastemanagement.service.AdminService;
import com.cleanbharat.wastemanagement.dto.SuccessResponse;
import com.cleanbharat.wastemanagement.dto.admin.DashboardResponse;
import com.cleanbharat.wastemanagement.dto.admin.UserDetailsResponse;
import com.cleanbharat.wastemanagement.dto.admin.UserSummaryResponse;
import com.cleanbharat.wastemanagement.enums.Role;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST APIs used by the
 * Admin Portal.
 */
@RestController
@RequestMapping("/api/admin")
@RequiredArgsConstructor
public class AdminController {

    // Admin service
    private final AdminService adminService;


    /**
     * Search reports using title, city, state or pincode.

     * Example:
     * /api/admin/reports/search?keyword=patna
     */
    @GetMapping("/reports/search")
    public ResponseEntity<List<ReportResponse>> searchReports(@RequestParam String keyword) {
        return ResponseEntity.ok(adminService.searchReports(keyword));
    }


    /**
     * Filter reports.
     * Every parameter is optional.

     * Examples:
     * /api/admin/reports/filter?status=PENDING
     * /api/admin/reports/filter?city=Patna
     * /api/admin/reports/filter?status=RESOLVED&city=Gaya
     */
    @GetMapping("/reports/filter")
    public ResponseEntity<List<ReportResponse>> filterReports(
            @RequestParam(required = false)
            ReportStatus status,

            @RequestParam(required = false)
            String city,

            @RequestParam(required = false)
            String state
    ) {
        return ResponseEntity.ok(adminService.filterReports(status, city, state));
    }

    /**
     * Deletes a report together with every dependent resource.

     * Deletes:
     * - Report image
     * - Votes
     * - Comments
     * - Cleanup Assignment
     * - Reward History
     * - Public Feed Analytics
     */
    @DeleteMapping("/reports/{reportId}")
    public ResponseEntity<SuccessResponse> deleteReport(@PathVariable Long reportId){
        SuccessResponse response = adminService.deleteReport(reportId);
        return ResponseEntity.ok(response);
    }

    /**
     * Returns overall statistics required for the Admin Dashboard.
     */
    @GetMapping("/dashboard")
    public ResponseEntity<DashboardResponse> getDashboard() {
        DashboardResponse response = adminService.getDashboard();
        return ResponseEntity.ok(response);
    }

    /**
     * Returns every registered user.

     * Optional role filter:
     * /api/admin/users
     * /api/admin/users?role=ROLE_CLEANER
     */
    @GetMapping("/users")
    public ResponseEntity<List<UserSummaryResponse>> getUsers(
            @RequestParam(required = false) Role role
    ){

        if (role == null) {
            return ResponseEntity.ok(adminService.getAllUsers());
        }

        return ResponseEntity.ok(adminService.getUsersByRole(role));
    }

    /**
     * Searches users by name or email.
     * Optional role filter.
     */
    @GetMapping("/users/search")
    public ResponseEntity<List<UserSummaryResponse>> searchUsers(
            @RequestParam String keyword,
            @RequestParam(required = false) Role role
    ) {
        return ResponseEntity.ok(
                adminService.searchUsers(keyword, role)
        );
    }

    /**
     * Returns complete details of one user.
     */
    @GetMapping("/users/{userId}")
    public ResponseEntity<UserDetailsResponse> getUserDetails(@PathVariable Long userId){
        return ResponseEntity.ok(
                adminService.getUserDetails(userId)
        );
    }

    /**
     * Deletes a citizen or cleaner.

     * Business Rules:
     * - Admin cannot be deleted
     * - Cleaner having assignments
     *   cannot be deleted
     */
    @DeleteMapping("/users/{userId}")
    public ResponseEntity<SuccessResponse> deleteUser(@PathVariable Long userId){
        SuccessResponse response = adminService.deleteUser(userId);
        return ResponseEntity.ok(response);
    }


    /**
     * Promotes a citizen to Admin.

     * Business Rules:
     * - Only citizens can be promoted
     * - Existing admins cannot be promoted
     * - Cleaners cannot be promoted
     */
    @PutMapping("/users/{userId}/promote")
    public ResponseEntity<SuccessResponse> promoteCitizenToAdmin(@PathVariable Long userId){
        SuccessResponse response = adminService.promoteCitizenToAdmin(userId);
        return ResponseEntity.ok(response);
    }
}