package com.cleanbharat.wastemanagement.service;

import com.cleanbharat.wastemanagement.dto.CreateReportRequest;
import com.cleanbharat.wastemanagement.dto.ReportResponse;

import java.util.List;

public interface ReportService {

    ReportResponse createReport(CreateReportRequest request); // create report

    List<ReportResponse> getAllReports(); // all reports

    ReportResponse getReport(Long id); // report by id

    List<ReportResponse> getMyReports(); // logged-in user's reports
}