package com.cleanbharat.wastemanagement.controller;

import com.cleanbharat.wastemanagement.dto.CreateReportRequest;
import com.cleanbharat.wastemanagement.dto.ReportResponse;
import com.cleanbharat.wastemanagement.service.ReportService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

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
    public ResponseEntity<List<ReportResponse>> getAllReports() {
        return ResponseEntity.ok(
                reportService.getAllReports()
        );
    }

    @GetMapping("/{id}") // GET /api/reports/1
    public ResponseEntity<ReportResponse> getReport(@PathVariable Long id) {
        return ResponseEntity.ok(reportService.getReport(id));
    }

    @GetMapping("/my") // GET /api/reports/my
    public ResponseEntity<List<ReportResponse>> getMyReports() {
        return ResponseEntity.ok(reportService.getMyReports());
    }
}