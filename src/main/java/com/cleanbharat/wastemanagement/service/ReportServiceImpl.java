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
import org.springframework.web.multipart.MultipartFile;
import java.util.List;

@Service
@RequiredArgsConstructor // constructor injection
public class ReportServiceImpl implements ReportService {

    private final GarbageReportRepository reportRepository; // report repo
    private final UserRepository userRepository; // user repo
    private final CloudinaryService cloudinaryService; // cloudinary service for image upload


    @Override
    public ReportResponse createReport(CreateReportRequest request, MultipartFile image) {
        // Upload image to Cloudinary
        String imageUrl = cloudinaryService.uploadFile(image);

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication(); // logged user
        String email = authentication.getName(); // user email

        User user = userRepository.findByEmail(email)       // find user
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        GarbageReport report = GarbageReport.builder()
                .title(request.getTitle()) // report title
                .description(request.getDescription()) // garbage details
                .latitude(request.getLatitude()) // GPS latitude
                .longitude(request.getLongitude()) // GPS longitude
                .address(request.getAddress()) // full address
                .landmark(request.getLandmark()) // nearby landmark
                .city(request.getCity()) // city name
                .state(request.getState()) // state name
                .pincode(request.getPincode()) // postal code
                .imageUrl(imageUrl) // cloudinary image URL
                .status(ReportStatus.PENDING) // default status
                .user(user) // report owner
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
                .id(report.getId()) // report id
                .title(report.getTitle()) // title
                .description(report.getDescription()) // description
                .latitude(report.getLatitude()) // GPS latitude
                .longitude(report.getLongitude()) // GPS longitude
                .address(report.getAddress()) // full address
                .landmark(report.getLandmark()) // nearby landmark
                .city(report.getCity()) // city
                .state(report.getState()) // state
                .pincode(report.getPincode()) // postal code
                .imageUrl(report.getImageUrl()) // cloudinary image
                .status(report.getStatus().name()) // enum -> String
                .urgencyScore(report.getUrgencyScore()) // average citizen rating
                .engagementScore(report.getEngagementScore()) // urgency + discussion score
                .reportedBy(report.getUser().getName()) // citizen name
                .createdAt(report.getCreatedAt()) // creation timestamp
                .build();
    }
}