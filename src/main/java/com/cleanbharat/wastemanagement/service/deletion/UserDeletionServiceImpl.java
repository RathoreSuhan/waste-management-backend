package com.cleanbharat.wastemanagement.service.deletion;

import com.cleanbharat.wastemanagement.entity.GarbageReport;
import com.cleanbharat.wastemanagement.entity.User;
import com.cleanbharat.wastemanagement.exception.UserDeletionNotAllowedException;
import com.cleanbharat.wastemanagement.repository.*;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * Handles deletion of users.
 */
@Service
@RequiredArgsConstructor
@Transactional
public class UserDeletionServiceImpl implements UserDeletionService {

    // User repository
    private final UserRepository userRepository;

    // Report repository
    private final GarbageReportRepository garbageReportRepository;

    // Vote repository
    private final VoteRepository voteRepository;

    // Comment repository
    private final CommentRepository commentRepository;

    // Report deletion service
    private final ReportDeletionService reportDeletionService;

    //Cleanup Assignment Repository
    private final CleanupAssignmentRepository cleanupAssignmentRepository;


    @Override
    public void deleteCitizen(User citizen) {

        /*
         * Step 1
         * Delete votes.
         */
        if (voteRepository.existsByUser(citizen)) {
            int deletedVotes = voteRepository.deleteByUser(citizen);
        }


        /*
         * Step 2
         * Delete comments.
         */
        if (commentRepository.existsByUser(citizen)) {
            int deletedComments = commentRepository.deleteByUser(citizen);
        }

        /*
         * Step 3
         * Delete every report.
         *
         * The likes this citizen gave are already gone: a like is kept
         * alongside their rating in the votes table, so deleting their
         * votes in step 1 removed them as well.
         */
        List<GarbageReport> reports = garbageReportRepository.findByUser(citizen);

        for (GarbageReport report : reports) {
            reportDeletionService.deleteReport(report);
        }


        /*
         * Step 4
         * Delete citizen.
         */
        userRepository.delete(citizen);

    }

    @Override
    public void deleteCleaner(User cleaner) {

        /*
         * Cleaners do not own reports.
         * They can own comments and votes.
         */
        if (commentRepository.existsByUser(cleaner)) {
            int deletedComments = commentRepository.deleteByUser(cleaner);
        }


        if (commentRepository.existsByUser(cleaner)) {
            int deletedComments = commentRepository.deleteByUser(cleaner);
        }

        /*
         * A cleaner can rate reports and appreciate other people's
         * cleanups. Both are recorded in the votes table, so removing
         * their votes removes their likes with them.
         */
        if (voteRepository.existsByUser(cleaner)) {
            voteRepository.deleteByUser(cleaner);
        }

        /*
         * Assignment cleanup is performed
         * through ReportDeletionService when
         * reports are deleted.

         *
         * For cleaner deletion, assignments
         * should already have been reassigned
         * or be handled by future workflow.
         */

        if (cleanupAssignmentRepository.existsByCleaner(cleaner)) {
            throw new UserDeletionNotAllowedException("Cleaner cannot be deleted because cleanup assignments are associated with this account.");
        }
        userRepository.delete(cleaner);
    }
}