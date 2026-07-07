package com.cleanbharat.wastemanagement.service;

import com.cleanbharat.wastemanagement.dto.CreateReportRequest;
import com.cleanbharat.wastemanagement.dto.ReportResponse;
import com.cleanbharat.wastemanagement.entity.GarbageReport;
import com.cleanbharat.wastemanagement.entity.User;
import com.cleanbharat.wastemanagement.enums.ReportStatus;
import com.cleanbharat.wastemanagement.exception.InvalidReportCreationException;
import com.cleanbharat.wastemanagement.exception.ResourceNotFoundException;
import com.cleanbharat.wastemanagement.repository.GarbageReportRepository;
import com.cleanbharat.wastemanagement.repository.UserRepository;
import com.cleanbharat.wastemanagement.enums.Role;
import com.cleanbharat.wastemanagement.mapper.ReportMapper;
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
    private final CleanupAssignmentService cleanupAssignmentService; // service to create cleanup assignment
    private final ReportMapper reportMapper; // Shared mapper for Report -> DTO conversion


    @Override
    public ReportResponse createReport(CreateReportRequest request, MultipartFile image) {
        // Upload image to Cloudinary
        String imageUrl = cloudinaryService.uploadFile(image);

        Authentication authentication = SecurityContextHolder.getContext().getAuthentication(); // logged user
        String email = authentication.getName(); // user email

        User user = userRepository.findByEmail(email)       // find user
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        // Only citizens can create garbage reports
        if (user.getRole() != Role.ROLE_CITIZEN) {
            throw new InvalidReportCreationException("Only citizens can create garbage reports.");
        }

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

        // Automatically create cleanup assignment
        cleanupAssignmentService.createDefaultAssignment(savedReport);

        return reportMapper.toResponse(savedReport);
    }

    @Override
    public List<ReportResponse> getAllReports() {
        return reportRepository.findAll()
                .stream()
                .map(reportMapper::toResponse) // entity -> dto
                .toList();
    }

    @Override
    public ReportResponse getReport(Long id) {
        GarbageReport report =
                reportRepository.findById(id)
                        .orElseThrow(() -> new ResourceNotFoundException("Report not found"));

        return reportMapper.toResponse(report);
    }

    @Override
    public List<ReportResponse> getMyReports() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication(); // logged user
        String email = authentication.getName(); // email

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        return reportRepository.findByUser(user)
                .stream()
                .map(reportMapper::toResponse) // entity -> dto
                .toList();
    }
}