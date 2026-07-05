package com.cleanbharat.wastemanagement.repository;

import com.cleanbharat.wastemanagement.entity.User;
import com.cleanbharat.wastemanagement.enums.Role;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    // Find user by email
    Optional<User> findByEmail(String email);

    // Check whether email already exists
    boolean existsByEmail(String email);

    /**
     * Returns Top 10 cleaners ordered
     * by highest reward points.
     */
    List<User> findTop10ByRoleOrderByRewardPointsDesc(Role role);

    /**
     * Returns the number of cleaners
     * having more reward points than
     * the given cleaner.

     * Used for rank calculation.
     */
    @Query("""
            SELECT COUNT(u)
            FROM User u
            WHERE u.role = :role
              AND u.rewardPoints > :rewardPoints
            """)
    long countByRoleAndRewardPointsGreaterThan(
            @Param("role") Role role,
            @Param("rewardPoints") Integer rewardPoints
    );

    /**
     * Returns Top 10 cleaners
     * belonging to a state.
     */
    List<User> findTop10ByRoleAndStateOrderByRewardPointsDesc(
            Role role,
            String state
    );

    /**
     * Returns Top 10 cleaners
     * belonging to a city.
     */
    List<User> findTop10ByRoleAndCityOrderByRewardPointsDesc(
            Role role,
            String city
    );

}