package com.cleanbharat.wastemanagement.controller;

import com.cleanbharat.wastemanagement.dto.CreateReportRequest;
import com.cleanbharat.wastemanagement.dto.MyReportSummaryResponse;
import com.cleanbharat.wastemanagement.dto.ReportResponse;
import com.cleanbharat.wastemanagement.dto.common.PageResponse;
import com.cleanbharat.wastemanagement.enums.ReportStatus;
import com.cleanbharat.wastemanagement.service.ReportService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

@RestController // REST API controller
@RequestMapping("/api/reports") // base url
@RequiredArgsConstructor // constructor injection
public class ReportController {
    private final ReportService reportService; // service layer

    /**
     * POST /api/reports
     *
     * The report fields are bound straight into the DTO from the multipart
     * form, which is what makes the bean constraints on CreateReportRequest
     * run. Building the DTO by hand here would skip validation entirely.
     *
     * Form field names are unchanged, so existing clients keep working.
     */
    @PostMapping(consumes = "multipart/form-data")
    public ResponseEntity<ReportResponse> createReport(
            @Valid @ModelAttribute CreateReportRequest request,

            @RequestParam("image")
            MultipartFile image
    ) {
        ReportResponse response = reportService.createReport(request, image);

        return ResponseEntity.ok(response);
    }

    @GetMapping // GET /api/reports
    public ResponseEntity<PageResponse<ReportResponse>> getAllReports(

            // Zero-based page index
            @RequestParam(defaultValue = "0")
            int page,

            // Rows per page
            @RequestParam(defaultValue = "10")
            int size,

            // Whitelisted sort property; ignored when not one of them
            @RequestParam(required = false)
            String sortBy,

            // "asc" or "desc"
            @RequestParam(required = false)
            String direction,

            // Search across title, city, state and pincode
            @RequestParam(required = false)
            String keyword,

            // One status, or omitted for every status
            @RequestParam(required = false)
            ReportStatus status
    ) {
        return ResponseEntity.ok(
                reportService.getAllReports(
                        page,
                        size,
                        sortBy,
                        direction,
                        keyword,
                        status
                )
        );
    }

    @GetMapping("/{id}") // GET /api/reports/1
    public ResponseEntity<ReportResponse> getReport(@PathVariable Long id) {
        return ResponseEntity.ok(reportService.getReport(id));
    }

    @GetMapping("/my") // GET /api/reports/my
    public ResponseEntity<PageResponse<ReportResponse>> getMyReports(
            @RequestParam(defaultValue = "0")
            int page,

            @RequestParam(defaultValue = "10")
            int size,

            // Optional status filter
            @RequestParam(required = false)
            ReportStatus status
    ) {
        return ResponseEntity.ok(
                reportService.getMyReports(page, size, status)
        );
    }

    /**
     * GET /api/reports/my/summary
     *
     * The count tiles above the citizen's own list. Separate from the list
     * call because the tiles describe every report the citizen has filed,
     * while /my returns only the page currently on screen.
     */
    @GetMapping("/my/summary")
    public ResponseEntity<MyReportSummaryResponse> getMyReportsSummary() {
        return ResponseEntity.ok(reportService.getMyReportsSummary());
    }
}