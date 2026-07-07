package com.cleanbharat.wastemanagement.repository;

import com.cleanbharat.wastemanagement.entity.GarbageReport;
import com.cleanbharat.wastemanagement.entity.User;
import com.cleanbharat.wastemanagement.entity.Vote;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.transaction.annotation.Transactional;
import java.util.List;
import java.util.Optional;

public interface VoteRepository extends JpaRepository<Vote, Long> {

    // Check whether citizen already voted on this report
    Optional<Vote> findByUserAndReport(User user, GarbageReport report);

    // Get all votes of a report
    List<Vote> findByReport(GarbageReport report);


    /**
     * Number of votes submitted by a user.
     */
    long countByUser(User user);


    /**
     * Deletes all votes belonging to a report.
     */
    @Modifying
    @Transactional
    int deleteByReport(GarbageReport report);

    /**
     * Deletes all votes submitted by a user.
     */
    @Modifying
    @Transactional
    int deleteByUser(User user);

    /**
     * Checks whether a report has votes.
     */
    boolean existsByReport(GarbageReport report);

    boolean existsByUser(User user);
}