package com.cleanbharat.wastemanagement.controller;

import com.cleanbharat.wastemanagement.dto.CreateReportRequest;
import com.cleanbharat.wastemanagement.dto.ReportResponse;
import com.cleanbharat.wastemanagement.service.ReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController // REST API controller
@RequestMapping("/api/reports") // base url
@RequiredArgsConstructor // constructor injection
public class ReportController {
    private final ReportService reportService; // service layer

    @PostMapping // POST /api/reports
    public ResponseEntity<ReportResponse> createReport(@RequestBody CreateReportRequest request) {
        ReportResponse response = reportService.createReport(request);
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