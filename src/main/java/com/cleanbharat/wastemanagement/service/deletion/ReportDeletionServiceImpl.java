package com.cleanbharat.wastemanagement.service.deletion;

import com.cleanbharat.wastemanagement.entity.GarbageReport;
import com.cleanbharat.wastemanagement.repository.CleanupAssignmentRepository;
import com.cleanbharat.wastemanagement.repository.CommentRepository;
import com.cleanbharat.wastemanagement.repository.GarbageReportRepository;
import com.cleanbharat.wastemanagement.repository.VoteRepository;
import com.cleanbharat.wastemanagement.service.CloudinaryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Deletes a garbage report and every resource owned by it.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class ReportDeletionServiceImpl implements ReportDeletionService {

    // Report repository
    private final GarbageReportRepository garbageReportRepository;

    // Cleanup assignment repository
    private final CleanupAssignmentRepository cleanupAssignmentRepository;

    // Vote repository
    private final VoteRepository voteRepository;

    // Comment repository
    private final CommentRepository commentRepository;

    // Assignment deletion service
    private final AssignmentDeletionService assignmentDeletionService;

    // Cloudinary service
    private final CloudinaryService cloudinaryService;

    @Override
    public void deleteReport(GarbageReport report) {

        /*
         * Step 1
         * Delete report image.
         */
        deleteReportImage(report);


        /*
         * Step 2
         * Delete cleanup assignment.
         */
        cleanupAssignmentRepository.findByReport(report)
                .ifPresent(assignmentDeletionService::deleteAssignment);


        /*
         * Step 3
         * Delete report votes.
         */
        if (voteRepository.existsByReport(report)) {
            int deletedVotes = voteRepository.deleteByReport(report);
        }


        /*
         * Step 4
         * Delete report comments.
         */
        if (commentRepository.existsByReport(report)) {
            int deletedComments = commentRepository.deleteByReport(report);
        }


        /*
         * Step 5
         * Delete report.
         */
        garbageReportRepository.delete(report);
    }

    /**
     * Deletes report image from Cloudinary.
     */
    private void deleteReportImage(GarbageReport report) {

        String imageUrl = report.getImageUrl();

        if (imageUrl == null || imageUrl.isBlank()) {
            return;
        }

        cloudinaryService.deleteFile(imageUrl);
    }
}