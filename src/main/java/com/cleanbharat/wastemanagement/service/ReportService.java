package com.cleanbharat.wastemanagement.service;

import com.cleanbharat.wastemanagement.dto.CreateReportRequest;
import com.cleanbharat.wastemanagement.dto.MyReportSummaryResponse;
import com.cleanbharat.wastemanagement.dto.ReportResponse;
import com.cleanbharat.wastemanagement.dto.common.PageResponse;
import com.cleanbharat.wastemanagement.enums.ReportStatus;
import org.springframework.web.multipart.MultipartFile;

public interface ReportService {

    // create report with image upload
    ReportResponse createReport(CreateReportRequest request, MultipartFile image);

    /**
     * One page of the public report register.
     *
     * The register used to be returned whole. Once it grew, every visitor
     * was downloading every report to look at ten of them, so the list is
     * cut into pages on the server instead.
     *
     * `keyword` and `status` are the register's two filters. They are
     * applied by the database rather than the browser, because filtering
     * one downloaded page answers a different question from the one the
     * reader asked.
     *
     * @param page      zero-based page index
     * @param size      rows per page, clamped by PaginationUtil
     * @param sortBy    a whitelisted property name; anything else is ignored
     * @param direction "asc" or "desc"
     * @param keyword   matches title, city, state or pincode; blank means none
     * @param status    one ReportStatus, or null for every status
     */
    PageResponse<ReportResponse> getAllReports(
            int page,
            int size,
            String sortBy,
            String direction,
            String keyword,
            ReportStatus status
    );

    ReportResponse getReport(Long id); // report by id

    // One page of the logged-in citizen's own reports, newest first
    PageResponse<ReportResponse> getMyReports(int page, int size, ReportStatus status);

    /**
     * Counts for the tiles above the citizen's own report list.
     *
     * Separate from the list: the tiles describe the whole collection while
     * the list shows one page of it, so they cannot be counted from the
     * page on screen.
     */
    MyReportSummaryResponse getMyReportsSummary();
}