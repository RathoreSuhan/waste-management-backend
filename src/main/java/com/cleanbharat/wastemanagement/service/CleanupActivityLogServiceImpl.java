package com.cleanbharat.wastemanagement.service;

import com.cleanbharat.wastemanagement.dto.CleanupActivityLogRequest;
import com.cleanbharat.wastemanagement.dto.CleanupActivityLogResponse;
import com.cleanbharat.wastemanagement.entity.CleanupActivityLog;
import com.cleanbharat.wastemanagement.entity.CleanupAssignment;
import com.cleanbharat.wastemanagement.entity.GarbageReport;
import com.cleanbharat.wastemanagement.entity.User;
import com.cleanbharat.wastemanagement.enums.AssignmentStatus;
import com.cleanbharat.wastemanagement.enums.Role;
import com.cleanbharat.wastemanagement.exception.InvalidAssignmentStateException;
import com.cleanbharat.wastemanagement.exception.ResourceNotFoundException;
import com.cleanbharat.wastemanagement.exception.UnauthorizedAssignmentAccessException;
import com.cleanbharat.wastemanagement.repository.CleanupActivityLogRepository;
import com.cleanbharat.wastemanagement.repository.CleanupAssignmentRepository;
import com.cleanbharat.wastemanagement.repository.UserRepository;
import com.cleanbharat.wastemanagement.util.GeoLocationUtil; // Haversine distance helper
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional // Image URL and diary row are persisted together, or not at all
public class CleanupActivityLogServiceImpl implements CleanupActivityLogService {

    private final CleanupActivityLogRepository activityLogRepository;
    private final CleanupAssignmentRepository assignmentRepository;
    private final UserRepository userRepository;
    private final CloudinaryService cloudinaryService; // optional per-entry evidence upload

    @Override
    public CleanupActivityLogResponse addActivityLog(Long assignmentId,
                                                     CleanupActivityLogRequest request,
                                                     MultipartFile image) {

        User cleaner = getLoggedInCleaner(); // role guard

        CleanupAssignment assignment = getOwnedAssignment(assignmentId, cleaner);

        validateWorkInProgress(assignment); // a diary only makes sense while work is running

        /*
         * Evidence is optional (requirement 3 and 7): a cleaner may simply type
         * "cleared the northern half today" without a photo or GPS fix.
         */
        String imageUrl = uploadActivityImage(image);

        CleanupActivityLog activityLog = CleanupActivityLog.builder()
                .assignment(assignment)
                .cleaner(cleaner)
                .description(request.getDescription())
                // Cleaner may back-date one visit (yesterday's shift); blank means "now"
                .activityAt(request.getActivityAt() != null ? request.getActivityAt() : LocalDateTime.now())
                .imageUrl(imageUrl)
                .latitude(request.getLatitude())
                .longitude(request.getLongitude())
                // Informational only: a diary entry is never rejected for being far away
                .distanceMeters(distanceFromReport(assignment.getReport(),
                        request.getLatitude(), request.getLongitude()))
                .build();

        return mapToResponse(activityLogRepository.save(activityLog));
    }

    @Override
    @Transactional(readOnly = true) // pure read, no flush needed
    public List<CleanupActivityLogResponse> getActivityLogs(Long assignmentId) {

        User cleaner = getLoggedInCleaner();

        CleanupAssignment assignment = getOwnedAssignment(assignmentId, cleaner);

        /*
         * Readable after the work ends too, so the cleaner can still see what
         * they submitted while the Municipal Corporation reviews the proof.
         */
        return activityLogRepository.findByAssignmentOrderByActivityAtAsc(assignment)
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Override
    public void deleteActivityLog(Long activityLogId) {

        User cleaner = getLoggedInCleaner();

        // findByIdAndCleaner keeps one cleaner from deleting another cleaner's entry
        CleanupActivityLog activityLog = activityLogRepository.findByIdAndCleaner(activityLogId, cleaner)
                .orElseThrow(() -> new ResourceNotFoundException("Activity log entry not found"));

        validateWorkInProgress(activityLog.getAssignment()); // history freezes once proof is submitted

        // Free the Cloudinary asset first; the row is worthless without it anyway
        if (activityLog.getImageUrl() != null) {
            cloudinaryService.deleteFile(activityLog.getImageUrl());
        }

        activityLogRepository.delete(activityLog);
    }

    // ---------------------------------------------------------------- helpers

    // Only cleaners keep a cleanup diary
    private User getLoggedInCleaner() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();

        User cleaner = userRepository.findByEmail(authentication.getName())
                .orElseThrow(() -> new ResourceNotFoundException("Logged-in user not found"));

        if (cleaner.getRole() != Role.ROLE_CLEANER) {
            throw new UnauthorizedAssignmentAccessException("Only cleaners can manage cleanup activity logs.");
        }
        return cleaner;
    }

    // The diary belongs to the assignment, and the assignment belongs to one authorised cleaner
    private CleanupAssignment getOwnedAssignment(Long assignmentId, User cleaner) {

        CleanupAssignment assignment = assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new ResourceNotFoundException("Cleanup assignment not found"));

        if (assignment.getCleaner() == null
                || !assignment.getCleaner().getId().equals(cleaner.getId())) {
            throw new UnauthorizedAssignmentAccessException(
                    "You are not the authorised cleaner for this cleanup task.");
        }
        return assignment;
    }

    /*
     * Entries may only be written while the site is actually being cleaned.
     * REWORK_REQUIRED counts as live work: the municipality sent the job back,
     * so the cleaner keeps working and must be able to record what they redid.
     */
    private void validateWorkInProgress(CleanupAssignment assignment) {
        AssignmentStatus status = assignment.getStatus();
        if (status != AssignmentStatus.IN_PROGRESS && status != AssignmentStatus.REWORK_REQUIRED) {
            throw new InvalidAssignmentStateException(
                    "Activity entries can only be added while the cleanup is in progress.");
        }
    }

    // An empty file part simply means "no photo for this entry"
    private String uploadActivityImage(MultipartFile image) {
        if (image == null || image.isEmpty()) {
            return null;
        }
        return cloudinaryService.uploadFile(image);
    }

    /*
     * Distance from the reported site, purely for the record.
     * Returns null when either side has no usable coordinates, so a missing GPS
     * fix never blocks the cleaner from writing down what they did.
     */
    private Double distanceFromReport(GarbageReport report, Double latitude, Double longitude) {
        if (latitude == null || longitude == null
                || !Double.isFinite(latitude) || !Double.isFinite(longitude)
                || report.getLatitude() == null || report.getLongitude() == null) {
            return null;
        }
        return GeoLocationUtil.calculateDistanceMeters(
                latitude, longitude, report.getLatitude(), report.getLongitude());
    }

    // Flattens the entity graph into the cleaner-facing DTO
    private CleanupActivityLogResponse mapToResponse(CleanupActivityLog activityLog) {
        return CleanupActivityLogResponse.builder()
                .activityLogId(activityLog.getId())
                .assignmentId(activityLog.getAssignment().getId())
                .description(activityLog.getDescription())
                .activityAt(activityLog.getActivityAt())
                .imageUrl(activityLog.getImageUrl())
                .latitude(activityLog.getLatitude())
                .longitude(activityLog.getLongitude())
                .distanceMeters(activityLog.getDistanceMeters())
                .cleanerName(activityLog.getCleaner().getName())
                .createdAt(activityLog.getCreatedAt())
                .build();
    }
}