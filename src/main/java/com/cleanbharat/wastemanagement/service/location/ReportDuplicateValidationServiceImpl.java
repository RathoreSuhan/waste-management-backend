package com.cleanbharat.wastemanagement.service.location;

import com.cleanbharat.wastemanagement.dto.CreateReportRequest;
import com.cleanbharat.wastemanagement.entity.GarbageReport;
import com.cleanbharat.wastemanagement.exception.DuplicateReportException;
import com.cleanbharat.wastemanagement.repository.GarbageReportRepository;
import com.cleanbharat.wastemanagement.util.GeoLocationUtil;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ReportDuplicateValidationServiceImpl implements ReportDuplicateValidationService {

    // Repository for report lookup
    private final GarbageReportRepository reportRepository;

    // Duplicate search radius (meters)
    @Value("${report.duplicate.radius-meters}")
    private Double duplicateRadiusMeters;

    // Duplicate search duration (days)
    @Value("${report.duplicate.days}")
    private Long duplicateDays;

    @Override
    public void validateNoDuplicateReport(CreateReportRequest request) {

        // Calculate latitude search window
        double latitudeDelta =
                GeoLocationUtil.calculateLatitudeDelta(
                        duplicateRadiusMeters
                );

        // Calculate longitude search window
        double longitudeDelta =
                GeoLocationUtil.calculateLongitudeDelta(
                        request.getLatitude(),
                        duplicateRadiusMeters
                );

        // Reports newer than configured duration
        LocalDateTime createdAfter = LocalDateTime.now().minusDays(duplicateDays);

        // Fetch only nearby candidate reports
        List<GarbageReport> nearbyReports =
                reportRepository.findNearbyRecentReports(
                        request.getPincode(),
                        request.getLatitude() - latitudeDelta,
                        request.getLatitude() + latitudeDelta,
                        request.getLongitude() - longitudeDelta,
                        request.getLongitude() + longitudeDelta,
                        createdAfter
                );

        // Perform exact distance calculation
        for (GarbageReport report : nearbyReports) {

            double distance =
                    GeoLocationUtil.calculateDistanceMeters(
                            request.getLatitude(),
                            request.getLongitude(),
                            report.getLatitude(),
                            report.getLongitude()
                    );

            // Duplicate report detected
            if (distance <= duplicateRadiusMeters) {

                throw new DuplicateReportException(

                        // Friendly message for frontend
                        "A nearby garbage report already exists. "
                                + "You can view, vote and comment on the existing report.",

                        // Existing report ID
                        report.getId(),

                        // Rounded distance in meters
                        (int) Math.round(distance),

                        // AI detected garbage category
                        report.getGarbageCategory()
                );
            }
        }
    }

}