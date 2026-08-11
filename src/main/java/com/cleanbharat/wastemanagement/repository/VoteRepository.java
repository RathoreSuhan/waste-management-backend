package com.cleanbharat.wastemanagement.repository;

import com.cleanbharat.wastemanagement.entity.GarbageReport;
import com.cleanbharat.wastemanagement.entity.User;
import com.cleanbharat.wastemanagement.entity.Vote;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
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


    /* ---------------------------------------------------------------
     * Ratings only
     *
     * A row may exist to record a like and carry no rating. Such a row
     * is not a vote, so the counts reported as "votes" must ask for a
     * rating to be present. Plain count() would include likes and
     * overstate every vote figure in the system.
     * --------------------------------------------------------------- */

    /**
     * Number of ratings submitted, system-wide.
     */
    long countByRatingIsNotNull();

    /**
     * Number of ratings submitted by a user.
     */
    long countByUserAndRatingIsNotNull(User user);


    /* ---------------------------------------------------------------
     * Likes
     * --------------------------------------------------------------- */

    /**
     * How many people appreciated this report's cleanup.
     *
     * This is the like total. It is counted from the rows rather than
     * read from a running tally, so it cannot drift from them.
     */
    long countByReportAndLikedTrue(GarbageReport report);

    /**
     * Whether this user's like of this report's cleanup stands.
     */
    boolean existsByUserAndReportAndLikedTrue(User user, GarbageReport report);

    /**
     * Withdraws every like on a report while keeping the ratings.
     *
     * Used when a cleanup is deleted but the report itself remains: the
     * likes appreciated a cleanup that no longer exists, yet the urgency
     * ratings still describe the garbage and must survive. Hence, an
     * update rather than a delete.
     */
    @Modifying
    @Transactional
    @Query("UPDATE Vote v SET v.liked = NULL WHERE v.report = :report AND v.liked = TRUE")
    int clearLikesByReport(@Param("report") GarbageReport report);

    /**
     * Removes rows that record neither a rating nor a like.
     *
     * Withdrawing a like from a row that never carried a rating leaves
     * an empty row; this clears those so the table holds only rows that
     * mean something.
     */
    @Modifying
    @Transactional
    @Query("DELETE FROM Vote v WHERE v.report = :report AND v.rating IS NULL AND v.liked IS NULL")
    int deleteEmptyRowsByReport(@Param("report") GarbageReport report);
}
