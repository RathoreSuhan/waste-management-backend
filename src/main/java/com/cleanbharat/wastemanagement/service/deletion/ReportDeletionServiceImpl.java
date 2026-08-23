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
 *
 * The cleanup hanging off the report goes first, and the report photo is
 * released only after the database has accepted the deletion.
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
         * Note down the report photo before the row is gone.
         *
         * The file itself is released in the last step, so a deletion the
         * database refuses cannot leave the report standing without its photo.
         */
        String reportImageUrl = report.getImageUrl();


        /*
         * Step 2
         * Delete cleanup assignment.
         *
         * This clears the proposals, the work diary, the municipal decisions
         * and every photo attached to them.
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

        // Send the deletion to the database before any file is touched
        garbageReportRepository.flush();


        /*
         * Step 6
         * Release the report photo noted in step 1.
         */
        deleteReportImage(reportImageUrl);
    }

    /**
     * Deletes report image from Cloudinary.
     */
    private void deleteReportImage(String imageUrl) {

        // Nothing to release for a report filed without a photo
        if (imageUrl == null || imageUrl.isBlank()) {
            return;
        }

        cloudinaryService.deleteFile(imageUrl);
    }
}