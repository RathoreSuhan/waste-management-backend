package com.cleanbharat.wastemanagement.service.deletion;

import com.cleanbharat.wastemanagement.entity.CleanupAssignment;
import com.cleanbharat.wastemanagement.entity.User;
import com.cleanbharat.wastemanagement.repository.CleanupAssignmentRepository;
import com.cleanbharat.wastemanagement.repository.PublicFeedAnalyticsRepository;
import com.cleanbharat.wastemanagement.repository.RewardHistoryRepository;
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

    // Reward history repository
    private final RewardHistoryRepository rewardHistoryRepository;

    // Public feed analytics repository
    private final PublicFeedAnalyticsRepository analyticsRepository;

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
         * Delete analytics if present.
         */
        analyticsRepository.findByCleanupAssignment(assignment)
                .ifPresent(analyticsRepository::delete);


        /*
         * Step 3
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
         * Step 4
         * Delete assignment.
         */
        cleanupAssignmentRepository.delete(assignment);
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