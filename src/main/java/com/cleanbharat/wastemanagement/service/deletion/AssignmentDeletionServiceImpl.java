package com.cleanbharat.wastemanagement.service.deletion;

import com.cleanbharat.wastemanagement.entity.CleanupAssignment;
import com.cleanbharat.wastemanagement.entity.GarbageReport;
import com.cleanbharat.wastemanagement.entity.User;
import com.cleanbharat.wastemanagement.repository.CleanupActivityLogRepository;
import com.cleanbharat.wastemanagement.repository.CleanupApprovalRepository;
import com.cleanbharat.wastemanagement.repository.CleanupAssignmentRepository;
import com.cleanbharat.wastemanagement.repository.CleanupProposalRepository;
import com.cleanbharat.wastemanagement.repository.PublicFeedAnalyticsRepository;
import com.cleanbharat.wastemanagement.repository.RewardHistoryRepository;
import com.cleanbharat.wastemanagement.repository.VoteRepository;

import com.cleanbharat.wastemanagement.repository.UserRepository;
import com.cleanbharat.wastemanagement.service.CloudinaryService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.LinkedHashSet; // keeps the photo list in order and free of duplicates

/**
 * Deletes a CleanupAssignment and every resource owned by it.
 *
 * The order matters twice over: rows are removed child-first so no foreign key
 * is ever left dangling, and the Cloudinary photos are released only once the
 * database has accepted the deletion.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class AssignmentDeletionServiceImpl implements AssignmentDeletionService {

    // Assignment repository
    private final CleanupAssignmentRepository cleanupAssignmentRepository;

    // Municipal decisions recorded on the proposals and on the finished work
    private final CleanupApprovalRepository cleanupApprovalRepository;

    // Competing cleanup plans submitted by cleaners for this site
    private final CleanupProposalRepository cleanupProposalRepository;

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
         * Note down every Cloudinary photo this cleanup owns.
         *
         * The links live inside rows that are about to disappear, so they are
         * read while they can still be read. The files themselves are released
         * at the very end, once the database has agreed to the deletion.
         */
        Collection<String> imageUrls = collectImageUrls(assignment);

        /*
         * The report outlives this cleanup, so it is kept aside before the bulk
         * deletes below detach the assignment from the persistence context.
         */
        GarbageReport report = assignment.getReport();


        /*
         * Step 2
         * Delete the municipal decisions taken on this cleanup.
         *
         * These come first because every decision points both at the assignment
         * and at the proposal it judged - leaving them behind would break both
         * links and the database would refuse the whole deletion.
         */
        cleanupApprovalRepository.deleteByAssignment(assignment);


        /*
         * Step 3
         * Delete the optional activity log.
         */
        cleanupActivityLogRepository.deleteByAssignment(assignment);


        /*
         * Step 4
         * Delete the proposals submitted for this site.
         *
         * The rows would also cascade away with the assignment, but clearing
         * them here keeps the deletion order explicit and spares the cascade a
         * row-by-row round trip.
         */
        cleanupProposalRepository.deleteByAssignment(assignment);


        /*
         * Step 5
         * Delete analytics if present.
         */
        analyticsRepository.findByCleanupAssignment(assignment)
                .ifPresent(analyticsRepository::delete);


        /*
         * Step 6
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
        voteRepository.clearLikesByReport(report);

        voteRepository.deleteEmptyRowsByReport(report);


        /*
         * Step 7
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
         * Step 8
         * Delete assignment.
         */

        cleanupAssignmentRepository.delete(assignment);

        /*
         * Push the deletion to the database now.
         *
         * Without this the statements would wait for the end of the
         * transaction, and the photos of step 9 would already be gone by the
         * time the database complained about something still pointing here.
         */
        cleanupAssignmentRepository.flush();


        /*
         * Step 9
         * Release the photos collected in step 1.
         *
         * Safe to do last: the rows that referenced them no longer exist.
         */
        releaseImages(imageUrls);
    }

    /**
     * Gathers the Cloudinary links held by this cleanup and everything under it.
     *
     * Reading only the links, instead of loading the proposals and diary
     * entries themselves, keeps this to two lightweight queries.
     */
    private Collection<String> collectImageUrls(CleanupAssignment assignment) {

        // A set, because the same photo asked for twice is a wasted call
        Collection<String> imageUrls = new LinkedHashSet<>();

        String cleanupImageUrl = assignment.getCleanupImageUrl();

        // Missing until the cleaner uploads the after-cleanup photo
        if (cleanupImageUrl != null && !cleanupImageUrl.isBlank()) {
            imageUrls.add(cleanupImageUrl);
        }

        // Site photos each cleaner attached while proposing a plan
        imageUrls.addAll(
                cleanupProposalRepository.findInspectionImageUrlsByAssignment(assignment)
        );

        // Progress photos from the work diary
        imageUrls.addAll(
                cleanupActivityLogRepository.findImageUrlsByAssignment(assignment)
        );

        return imageUrls;
    }

    /**
     * Deletes the collected photos from Cloudinary.
     *
     * A refused delete is logged rather than thrown, so one unreachable file
     * cannot undo the database work that already succeeded.
     */
    private void releaseImages(Collection<String> imageUrls) {

        for (String imageUrl : imageUrls) {
            cloudinaryService.deleteFile(imageUrl);
        }
    }
}