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
     * Returns Top 10 cleaners ordered by highest reward points.
     *
     * Ties are broken by id ascending so the board's sequential rank
     * numbers are deterministic: the same points ordering always produces
     * the same displayed ranks, which is what the personal ranking
     * (getMyRanking) must be able to reproduce exactly.
     */
    List<User> findTop10ByRoleOrderByRewardPointsDescIdAsc(Role role);

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
     * Returns the number of cleaners tied on the given reward points whose
     * id orders them BEFORE the given cleaner on the national board.
     *
     * The board ranks rows sequentially (points descending, id ascending
     * among ties), so a cleaner's displayed rank is the number of cleaners
     * strictly above them plus the number of equal-point cleaners ordered
     * ahead of them, plus one. Counting only strictly-higher points - the
     * old formula - answers the competition-style rank instead, and the two
     * diverge exactly when ties exist (the reported "board says 3, My
     * Standing says 2" mismatch).
     */
    long countByRoleAndRewardPointsAndIdLessThan(
            Role role,
            Integer rewardPoints,
            Long id
    );

    /*
      ========================================================================
      Leaderboard boards
      ========================================================================

      Each scoped board matches its location with LOWER(TRIM(...)) on BOTH
      sides - the submitted parameter is trimmed by the service, but the
      stored column values are whatever registration or the reconciliation
      pass wrote, so trimming only the input would still miss a stored
      ' S.A.S Nagar '. Case-folded, whitespace-trimmed comparison is what
      makes 'S.A.S Nagar', 'S.a.s Nagar' and 's.a.s nagar' one district.

      The Top-10 limit and ordering ride on the Pageable (a fixed first
      page); the JPQL carries the board's own ORDER BY reward points.
    */

    /**
     * Returns the Top 10 cleaners of a state, matched case- and
     * whitespace-insensitively against the stored state.
     */
    @Query("""
            SELECT u
            FROM User u
            WHERE u.role = :role
              AND LOWER(TRIM(u.state)) = LOWER(TRIM(:state))
            ORDER BY u.rewardPoints DESC
            """)
    List<User> findTop10InState(
            @Param("role") Role role,
            @Param("state") String state,
            Pageable topTen
    );

    /**
     * Returns the Top 10 cleaners of a city, matched case- and
     * whitespace-insensitively against the stored legacy city value.
     */
    @Query("""
            SELECT u
            FROM User u
            WHERE u.role = :role
              AND LOWER(TRIM(u.city)) = LOWER(TRIM(:city))
            ORDER BY u.rewardPoints DESC
            """)
    List<User> findTop10InCity(
            @Param("role") Role role,
            @Param("city") String city,
            Pageable topTen
    );

    /**
     * Returns the Top 10 cleaners of one district - every ULB inside it,
     * because the stored district is what the refactor's eligibility and
     * boards group on. The state is part of the match so two states naming
     * a district the same way can never share a board; the district is
     * never compared on its own.
     */
    @Query("""
            SELECT u
            FROM User u
            WHERE u.role = :role
              AND LOWER(TRIM(u.state)) = LOWER(TRIM(:state))
              AND LOWER(TRIM(u.district)) = LOWER(TRIM(:district))
            ORDER BY u.rewardPoints DESC
            """)
    List<User> findTop10InDistrict(
            @Param("role") Role role,
            @Param("state") String state,
            @Param("district") String district,
            Pageable topTen
    );

    /**
     * Returns the Top 10 cleaners accountable for one exact LGD ULB,
     * matched case- and whitespace-insensitively against the stored
     * ulb_code. A row without a reconciled code (NULL) can never match, so
     * the ULB board stays scoped to its own municipal jurisdiction.
     */
    @Query("""
            SELECT u
            FROM User u
            WHERE u.role = :role
              AND LOWER(TRIM(u.ulbCode)) = LOWER(TRIM(:ulbCode))
            ORDER BY u.rewardPoints DESC
            """)
    List<User> findTop10ByUlbCode(
            @Param("role") Role role,
            @Param("ulbCode") String ulbCode,
            Pageable topTen
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