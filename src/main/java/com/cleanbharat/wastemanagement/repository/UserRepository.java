package com.cleanbharat.wastemanagement.repository;

import com.cleanbharat.wastemanagement.entity.User;
import com.cleanbharat.wastemanagement.enums.Role;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserRepository extends JpaRepository<User, Long> {

    // Find user by email
    Optional<User> findByEmail(String email);

    /**
     * Looks an account up by the Google account linked to it.
     *
     * This is the first question asked on every Google sign-in, and the only
     * one whose answer is certain: 'sub' is Google's permanent identifier for
     * the account, so a match here is the same person as last time even if
     * they have since changed the email address on it.
     */
    Optional<User> findByGoogleSubject(String googleSubject);

    /**
     * Looks an account up by email, ignoring case.
     *
     * Needed for the one-off link between a Google account and an account that
     * already existed. Google hands back a lower-cased address, while a row
     * created through the old sign-up form kept whatever the person typed -
     * "Suhan@Gmail.com". findByEmail above is exact, so matching with it would
     * miss that row and create a second account for the same person, which is
     * precisely what the link is there to prevent.
     */
    Optional<User> findByEmailIgnoreCase(String email);

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
     * Returns Top 10 cleaners belonging to a city.
     */
    List<User> findTop10ByRoleAndCityOrderByRewardPointsDesc(
            Role role,
            String city
    );


    /**
     * Counts users belonging to a specific role.
     */
    long countByRole(Role role);


    /**
     * Returns all users having the given role.
     */
    List<User> findByRole(Role role);


    /**
     * Search users by name or email.
     */
    List<User> findByNameContainingIgnoreCaseOrEmailContainingIgnoreCase(
            String name,
            String email
    );


    /**
     * Search users by role and keyword.
     */
    List<User> findByRoleAndNameContainingIgnoreCaseOrRoleAndEmailContainingIgnoreCase(
            Role role,
            String name,
            Role roleAgain,
            String email
    );


    /*
      ========================================================================
      Paged reads
      ========================================================================

      The admin user list is the one screen that genuinely grows without
      bound - every registration adds a row - so it is the one that most
      needs paging rather than a full download.

      These are derived queries, so Spring writes the count query itself and
      the pageable supplies the ORDER BY. The "property + Pageable" overloads
      sit alongside the plain ones rather than replacing them: the plain
      versions are still what the shorter internal lookups use.
    */

    // One page of every registered user
    Page<User> findAllBy(Pageable pageable);

    // One page of users in a given role
    Page<User> findByRole(Role role, Pageable pageable);

    // One page of name/email matches across every role
    Page<User> findByNameContainingIgnoreCaseOrEmailContainingIgnoreCase(
            String name,
            String email,
            Pageable pageable
    );

    // One page of name/email matches within a single role
    Page<User> findByRoleAndNameContainingIgnoreCaseOrRoleAndEmailContainingIgnoreCase(
            Role role,
            String name,
            Role roleAgain,
            String email,
            Pageable pageable
    );
}