package com.cleanbharat.wastemanagement.controller;

import com.cleanbharat.wastemanagement.dto.ReportResponse;
import com.cleanbharat.wastemanagement.dto.common.PageResponse;
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
     * /api/admin/reports/search?keyword=patna&page=0&size=10
     */
    @GetMapping("/reports/search")
    public ResponseEntity<PageResponse<ReportResponse>> searchReports(
            @RequestParam String keyword,

            @RequestParam(defaultValue = "0")
            int page,

            @RequestParam(defaultValue = "10")
            int size,

            @RequestParam(required = false)
            String sortBy,

            @RequestParam(required = false)
            String direction
    ) {
        return ResponseEntity.ok(
                adminService.searchReports(keyword, page, size, sortBy, direction)
        );
    }


    /**
     * Filter reports.
     * Every filter parameter is optional.

     * Examples:
     * /api/admin/reports/filter?status=PENDING
     * /api/admin/reports/filter?city=Patna
     * /api/admin/reports/filter?status=RESOLVED&city=Gaya&page=1&size=25
     */
    @GetMapping("/reports/filter")
    public ResponseEntity<PageResponse<ReportResponse>> filterReports(
            @RequestParam(required = false)
            ReportStatus status,

            @RequestParam(required = false)
            String city,

            @RequestParam(required = false)
            String state,

            @RequestParam(defaultValue = "0")
            int page,

            @RequestParam(defaultValue = "10")
            int size,

            @RequestParam(required = false)
            String sortBy,

            @RequestParam(required = false)
            String direction
    ) {
        return ResponseEntity.ok(
                adminService.filterReports(
                        status,
                        city,
                        state,
                        page,
                        size,
                        sortBy,
                        direction
                )
        );
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
     * Returns one page of registered users.

     * Optional role filter:
     * /api/admin/users
     * /api/admin/users?role=ROLE_CLEANER
     * /api/admin/users?role=ROLE_CITIZEN&page=1&size=25
     */
    @GetMapping("/users")
    public ResponseEntity<PageResponse<UserSummaryResponse>> getUsers(
            @RequestParam(required = false) Role role,

            @RequestParam(defaultValue = "0")
            int page,

            @RequestParam(defaultValue = "10")
            int size,

            @RequestParam(required = false)
            String sortBy,

            @RequestParam(required = false)
            String direction
    ){
        return ResponseEntity.ok(
                adminService.getUsers(role, page, size, sortBy, direction)
        );
    }

    /**
     * Searches users by name or email.
     * Optional role filter.
     */
    @GetMapping("/users/search")
    public ResponseEntity<PageResponse<UserSummaryResponse>> searchUsers(
            @RequestParam String keyword,
            @RequestParam(required = false) Role role,

            @RequestParam(defaultValue = "0")
            int page,

            @RequestParam(defaultValue = "10")
            int size,

            @RequestParam(required = false)
            String sortBy,

            @RequestParam(required = false)
            String direction
    ) {
        return ResponseEntity.ok(
                adminService.searchUsers(
                        keyword,
                        role,
                        page,
                        size,
                        sortBy,
                        direction
                )
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