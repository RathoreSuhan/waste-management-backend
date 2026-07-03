package com.cleanbharat.wastemanagement.service;

import com.cleanbharat.wastemanagement.dto.PublicFeedResponse;
import com.cleanbharat.wastemanagement.entity.CleanupAssignment;
import com.cleanbharat.wastemanagement.exception.ResourceNotFoundException;
import com.cleanbharat.wastemanagement.repository.CleanupAssignmentRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PublicFeedServiceImpl implements PublicFeedService {

    // Repository for completed cleanup assignments
    private final CleanupAssignmentRepository assignmentRepository;

    @Override
    public List<PublicFeedResponse> getPublicFeed() {
        return assignmentRepository.findCompletedVerifiedAssignments()
                .stream()
                .map(this::mapToResponse) // Entity → DTO
                .toList();
    }

    @Override
    public PublicFeedResponse getPublicFeedByReportId(Long reportId) {

        CleanupAssignment assignment = assignmentRepository
                .findCompletedVerifiedAssignmentByReportId(reportId)
                .orElseThrow(() -> new ResourceNotFoundException("Completed AI-verified cleanup not found for report id: " + reportId));

        return mapToResponse(assignment);
    }

    /**
     * Converts CleanupAssignment into PublicFeedResponse.
     */
    private PublicFeedResponse mapToResponse(CleanupAssignment assignment) {

        return PublicFeedResponse.builder()

                // Garbage report details
                .reportId(assignment.getReport().getId())
                .reportTitle(assignment.getReport().getTitle())
                .reportDescription(assignment.getReport().getDescription())

                // Before & After cleanup images
                .beforeImageUrl(assignment.getReport().getImageUrl())
                .afterImageUrl(assignment.getCleanupImageUrl())

                // Cleanup location
                .address(assignment.getReport().getAddress())
                .landmark(assignment.getReport().getLandmark())
                .city(assignment.getReport().getCity())
                .state(assignment.getReport().getState())

                // Cleaner information
                .cleanerName(assignment.getCleaner().getName())
                .cleanerType(assignment.getCleaner().getCleanerType().name())

                // Municipal Corporation
                .municipalCorporationName(
                        assignment.getAssignedMunicipalCorporation()
                                .getOrganizationName()
                )

                // Cleanup completion time
                .cleanupCompletedTime(assignment.getCompletedAt())

                // Final report status
                .reportStatus(
                        assignment.getReport()
                                .getStatus()
                                .name()
                )

                // AI verification details
                .aiVerified(assignment.getAiVerified())
                .aiConfidence(assignment.getAiConfidence())
                .aiRemarks(assignment.getAiRemarks())

                .build();
    }
}