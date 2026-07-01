package com.cleanbharat.wastemanagement.repository;

import com.cleanbharat.wastemanagement.entity.RewardHistory;
import com.cleanbharat.wastemanagement.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RewardHistoryRepository extends JpaRepository<RewardHistory, Long> {
    /**
     * Returns complete reward history
     * of a cleaner.
     */
    List<RewardHistory> findByCleanerOrderByCreatedAtDesc(User cleaner);

}