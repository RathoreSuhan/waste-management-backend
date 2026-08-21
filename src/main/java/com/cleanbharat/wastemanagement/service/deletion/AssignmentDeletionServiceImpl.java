package com.cleanbharat.wastemanagement.service.deletion;

import com.cleanbharat.wastemanagement.entity.CleanupActivityLog;
import com.cleanbharat.wastemanagement.entity.CleanupAssignment;
import com.cleanbharat.wastemanagement.entity.User;
import com.cleanbharat.wastemanagement.repository.CleanupActivityLogRepository;
import com.cleanbharat.wastemanagement.repository.CleanupAssignmentRepository;
import com.cleanbharat.wastemanagement.repository.PublicFeedAnalyticsRepository;
import com.cleanbharat.wastemanagement.repository.RewardHistoryRepository;
import com.cleanbharat.wastemanagement.repository.VoteRepository;

import com.cleanbharat.wastemanagement.repository.UserRepository;
import com.cleanbharat.wastemanagement.service.CloudinaryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Deletes a CleanupAssignment and every resource owned by it.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class AssignmentDeletionServiceImpl implements AssignmentDeletionService {

    // Assignment repository
    private final CleanupAssignmentRepository cleanupAssignmentRepository;

    // Optional cleanup work diary written while the site was IN_PROGRESS
    private final CleanupActivityLogRepository cleanupActivityLogRepository;

    // Reward history repository
    private final RewardHistoryRepository rewardHistoryRepository;

    // Public feed analytics repository
    private final PublicFeedAnalyticsRepository analyticsRepository;

    // Holds each person's rating of a report and whether they
    // appreciated its cleanup
    private final VoteRepository voteRepository;


    // Cloudinary service
    private final CloudinaryService cloudinaryService;

    // User repository
    private final UserRepository userRepository;

    @Override
    public void deleteAssignment(CleanupAssignment assignment) {

        /*
         * Step 1
         * Delete cleanup image.
         */
        deleteCleanupImage(assignment);


        /*
         * Step 2
         * Delete the optional activity log.
         *
         * The rows would cascade away with the assignment, but the photos
         * attached to them would stay behind on Cloudinary, so each entry is
         * cleared explicitly first.
         */
        deleteActivityLogs(assignment);


        /*
         * Step 3
         * Delete analytics if present.
         */
        analyticsRepository.findByCleanupAssignment(assignment)
                .ifPresent(analyticsRepository::delete);


        /*
         * Step 4
         * Withdraw the likes people gave this cleanup.
         *
         * The report outlives the cleanup here, and a like belongs to the
         * cleanup that earned it - leaving them standing would carry the
         * appreciation over to whatever cleanup comes next.
         *
         * The urgency ratings kept alongside them describe the garbage
         * rather than the cleanup, so those stay: only the likes are
         * cleared, and rows left holding nothing at all are removed.
         */
        voteRepository.clearLikesByReport(assignment.getReport());

        voteRepository.deleteEmptyRowsByReport(assignment.getReport());


        /*
         * Step 5
         * Reverse cleaner reward points and delete reward history.
         */

        rewardHistoryRepository.findByAssignment(assignment)
                .ifPresent(rewardHistory -> {

                    // Cleaner who earned the reward
                    User cleaner = rewardHistory.getCleaner();

                    // Reverse awarded points
                    cleaner.setRewardPoints(
                            cleaner.getRewardPoints() - rewardHistory.getPoints()
                    );

                    // Persist updated reward points
                    userRepository.save(cleaner);

                    // Delete reward history
                    rewardHistoryRepository.delete(rewardHistory);
                });


        /*
         * Step 6
         * Delete assignment.
         */

        cleanupAssignmentRepository.delete(assignment);
    }

    /**
     * Removes every activity entry of this assignment together with its image.
     *
     * Silent no-op for the common case: most cleanups carry no diary at all.
     */
    private void deleteActivityLogs(CleanupAssignment assignment) {

        for (CleanupActivityLog activityLog :
                cleanupActivityLogRepository.findByAssignmentOrderByActivityAtAsc(assignment)) {

            String imageUrl = activityLog.getImageUrl();

            // Entries without a photo simply have nothing to release
            if (imageUrl != null && !imageUrl.isBlank()) {
                cloudinaryService.deleteFile(imageUrl);
            }
        }

        cleanupActivityLogRepository.deleteByAssignment(assignment);
    }

    /**
     * Deletes cleanup image from Cloudinary.
     */
    private void deleteCleanupImage(CleanupAssignment assignment) {

        String imageUrl = assignment.getCleanupImageUrl();

        /*
         * Ignore when no cleanup image has been uploaded.
         */
        if (imageUrl == null || imageUrl.isBlank()) {
            return;
        }

        cloudinaryService.deleteFile(imageUrl);
    }
}