package com.cleanbharat.wastemanagement.mapper;

import com.cleanbharat.wastemanagement.dto.ReportResponse;
import com.cleanbharat.wastemanagement.entity.GarbageReport;
import org.springframework.stereotype.Component;

/**
 * Converts GarbageReport entity into ReportResponse DTO.
 */
@Component
public class ReportMapper {

    /**
     * Entity -> DTO
     */
    public ReportResponse toResponse(GarbageReport report) {

        return ReportResponse.builder()

                // Basic report information
                .id(report.getId())
                .title(report.getTitle())
                .description(report.getDescription())

                // Location
                .latitude(report.getLatitude())
                .longitude(report.getLongitude())
                .address(report.getAddress())
                .landmark(report.getLandmark())
                .city(report.getCity())
                .state(report.getState())
                .pincode(report.getPincode())

                // Uploaded image
                .imageUrl(report.getImageUrl())

                // Current report status
                .status(report.getStatus().name())

                // Analytics
                .urgencyScore(report.getUrgencyScore())
                .engagementScore(report.getEngagementScore())

                // Report creator
                .reportedBy(report.getUser().getName())

                // Creation timestamp
                .createdAt(report.getCreatedAt())

                .build();
    }
}