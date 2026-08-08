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
import com.cleanbharat.wastemanagement.util.LocationUtil;
import com.cleanbharat.wastemanagement.dto.ai.AIReportValidationResponse;
import com.cleanbharat.wastemanagement.service.ai.AIReportValidationService;
import com.cleanbharat.wastemanagement.service.location.ReportDuplicateValidationService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import java.util.List;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor // constructor injection
public class ReportServiceImpl implements ReportService {

    private final GarbageReportRepository reportRepository; // report repo
    private final UserRepository userRepository; // user repo
    private final CloudinaryService cloudinaryService; // cloudinary service for image upload
    private final CleanupAssignmentService cleanupAssignmentService; // service to create cleanup assignment
    private final ReportMapper reportMapper; // Shared mapper for Report -> DTO conversion
    private final AIReportValidationService aiReportValidationService; // AI validation
    private final ReportDuplicateValidationService reportDuplicateValidationService; // Duplicate validation


    @Transactional
    @Override
    public ReportResponse createReport(CreateReportRequest request, MultipartFile image) {

        // Logged-in user
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        String email = authentication.getName();

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new ResourceNotFoundException("User not found"));

        // Only citizens can create reports
        if (user.getRole() != Role.ROLE_CITIZEN) {
            throw new InvalidReportCreationException(
                    "Only citizens can create garbage reports."
            );
        }

        /*
         * Step 1
         * Clean up the submitted location text.
         *
         * Done before anything else so the duplicate check and the saved row
         * both work on the same values.
         */
        normalizeLocationFields(request);

        /*
         * Step 2
         * AI validates uploaded image.
         */
        AIReportValidationResponse aiResponse = aiReportValidationService.validateReportImage(image);

        /*
         * Step 3
         * Prevent duplicate reports.
         */
        reportDuplicateValidationService.validateNoDuplicateReport(request);


        String imageUrl = null;
        try {
            /*
             * Step 4
             * Upload image only after all validations pass.
             */
            imageUrl = cloudinaryService.uploadFile(image);

            /*
             * Step 5
             * Create report entity.
             */
            GarbageReport report = GarbageReport.builder()
                    .title(request.getTitle())
                    .description(request.getDescription())
                    .latitude(request.getLatitude())
                    .longitude(request.getLongitude())
                    .address(request.getAddress())
                    .landmark(request.getLandmark())
                    .city(request.getCity())
                    .state(request.getState())
                    .pincode(request.getPincode())

                    // AI detected garbage category
                    .garbageCategory(aiResponse.getGarbageCategory())

                    .imageUrl(imageUrl)
                    .status(ReportStatus.PENDING)
                    .user(user)
                    .build();

            /*
             * Step 6
             * Save report.
             */
            GarbageReport savedReport = reportRepository.save(report);
            log.info("Creating cleanup assignment for report {}", savedReport.getId());

            /*
             * Step 7
             * Automatically create cleanup assignment.
             */
            cleanupAssignmentService.createDefaultAssignment(savedReport);
            log.info("Cleanup assignment created successfully.");

            return reportMapper.toResponse(savedReport);
        }catch (Exception ex) {

            // Database transaction will roll back automatically
            // Remove uploaded image from Cloudinary
            if (imageUrl != null) {
                cloudinaryService.deleteFile(imageUrl);
            }
            throw ex;
        }
    }

    /**
     * Brings the submitted location text to a consistent shape.
     *
     * City and state are title-cased the same way they are for users at
     * registration, so "kolkata", "Kolkata" and "KOLKATA" all become the one
     * value. Reports are grouped and matched by city, so without this the
     * same place would split into several distinct entries.
     *
     * Only whitespace is stripped from the address, landmark and pincode,
     * since those are free text rather than grouping keys.
     *
     * Blank fields are already rejected by the bean constraints on
     * CreateReportRequest, so city and state are safe to normalize here.
     */
    private void normalizeLocationFields(CreateReportRequest request) {

        request.setCity(LocationUtil.normalizeLocation(request.getCity()));
        request.setState(LocationUtil.normalizeLocation(request.getState()));

        request.setAddress(request.getAddress().trim());
        request.setPincode(request.getPincode().trim());

        // Landmark is optional, so an omitted or empty value is stored as null
        String landmark = request.getLandmark();

        if (landmark == null || landmark.isBlank()) {
            request.setLandmark(null);
        } else {
            request.setLandmark(landmark.trim());
        }
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