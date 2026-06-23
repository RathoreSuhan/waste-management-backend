package com.cleanbharat.wastemanagement.repository;

import com.cleanbharat.wastemanagement.entity.GarbageReport;
import com.cleanbharat.wastemanagement.entity.User;
import com.cleanbharat.wastemanagement.entity.Vote;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;
import java.util.Optional;

public interface VoteRepository extends JpaRepository<Vote, Long> {

    // Check whether citizen already voted on this report
    Optional<Vote> findByUserAndReport(User user, GarbageReport report);

    // Get all votes of a report
    List<Vote> findByReport(GarbageReport report);
}