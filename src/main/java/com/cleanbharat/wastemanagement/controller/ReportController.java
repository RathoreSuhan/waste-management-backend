package com.cleanbharat.wastemanagement.controller;

import com.cleanbharat.wastemanagement.dto.CreateReportRequest;
import com.cleanbharat.wastemanagement.dto.ReportResponse;
import com.cleanbharat.wastemanagement.service.ReportService;
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

    @PostMapping(consumes = "multipart/form-data")      // POST /api/reports
    public ResponseEntity<ReportResponse> createReport(
            @RequestParam String title,
            @RequestParam String description,
            @RequestParam String location,
            @RequestParam("image")
            MultipartFile image
    ) {
        CreateReportRequest request = new CreateReportRequest();

        request.setTitle(title);
        request.setDescription(description);
        request.setLocation(location);

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