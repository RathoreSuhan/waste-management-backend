package com.cleanbharat.wastemanagement.service;

import com.cleanbharat.wastemanagement.dto.CreateReportRequest;
import com.cleanbharat.wastemanagement.dto.ReportResponse;
import org.springframework.web.multipart.MultipartFile;

import java.util.List;

public interface ReportService {

    // create report with image upload
    ReportResponse createReport(CreateReportRequest request, MultipartFile image);

    List<ReportResponse> getAllReports(); // all reports

    ReportResponse getReport(Long id); // report by id

    List<ReportResponse> getMyReports(); // logged-in user's reports
}