package com.cleanbharat.wastemanagement.service;

import com.cleanbharat.wastemanagement.dto.CreateReportRequest;
import com.cleanbharat.wastemanagement.dto.ReportResponse;
import com.cleanbharat.wastemanagement.entity.GarbageReport;
import com.cleanbharat.wastemanagement.entity.User;
import com.cleanbharat.wastemanagement.enums.ReportStatus;
import com.cleanbharat.wastemanagement.exception.ResourceNotFoundException;
import com.cleanbharat.wastemanagement.repository.GarbageReportRepository;
import com.cleanbharat.wastemanagement.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor // constructor injection
public class ReportServiceImpl implements ReportService {

    private final GarbageReportRepository reportRepository; // report repo
    private final UserRepository userRepository; // user repo

    @Override
    public ReportResponse createReport(CreateReportRequest request) {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication(); // logged user
        String email = authentication.getName(); // user email

        User user = userRepository.findByEmail(email)       // find user
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        GarbageReport report = GarbageReport.builder()
                .title(request.getTitle())
                .description(request.getDescription())
                .location(request.getLocation())
                .imageUrl(request.getImageUrl())
                .status(ReportStatus.PENDING) // default status
                .user(user)
                .build();

        GarbageReport savedReport = reportRepository.save(report); // save report

        return mapToResponse(savedReport);
    }

    @Override
    public List<ReportResponse> getAllReports() {
        return reportRepository.findAll()
                .stream()
                .map(this::mapToResponse) // entity -> dto
                .toList();
    }

    @Override
    public ReportResponse getReport(Long id) {
        GarbageReport report =
                reportRepository.findById(id)
                        .orElseThrow(() -> new ResourceNotFoundException("Report not found"));

        return mapToResponse(report);
    }

    @Override
    public List<ReportResponse> getMyReports() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication(); // logged user
        String email = authentication.getName(); // email

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        return reportRepository.findByUser(user)
                .stream()
                .map(this::mapToResponse) // entity -> dto
                .toList();
    }

    private ReportResponse mapToResponse(GarbageReport report) {
        return ReportResponse.builder()
                .id(report.getId())
                .title(report.getTitle())
                .description(report.getDescription())
                .location(report.getLocation())
                .imageUrl(report.getImageUrl())
                .status(report.getStatus().name())
                .reportedBy(report.getUser().getName())
                .createdAt(report.getCreatedAt())
                .build();
    }
}