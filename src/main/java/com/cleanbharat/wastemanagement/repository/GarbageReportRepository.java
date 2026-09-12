package com.cleanbharat.wastemanagement.repository;

import com.cleanbharat.wastemanagement.entity.GarbageReport;
import com.cleanbharat.wastemanagement.entity.User;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import com.cleanbharat.wastemanagement.enums.ReportStatus;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;
import java.util.List;

@Repository
public interface GarbageReportRepository extends JpaRepository<GarbageReport, Long> {

    // Reports created by a specific user
    List<GarbageReport> findByUser(User user);

    /*
      ========================================================================
      Paged reads
      ========================================================================

      Every report sent to a client carries its reporter's name - the mapper
      reads report.getUser().getName(). `user` is a lazy ManyToOne, so a page
      of ten reports would otherwise cost ten further SELECTs to resolve ten
      names, on top of the one query that fetched the page.

      @EntityGraph(attributePaths = "user") tells Hibernate to join the user
      into that first query, so a page is one round trip regardless of size.

      The paged finders take their ORDER BY from the Pageable rather than
      naming a property here, which is what lets the same query serve
      "newest first" and "most engaged first".
    */

    // One page of the whole register
    @EntityGraph(attributePaths = "user")
    Page<GarbageReport> findAllBy(Pageable pageable);

    // One page of a single citizen's reports
    @EntityGraph(attributePaths = "user")
    Page<GarbageReport> findByUser(User user, Pageable pageable);

    // One page of a single citizen's reports in one status
    @EntityGraph(attributePaths = "user")
    Page<GarbageReport> findByUserAndStatus(User user, ReportStatus status, Pageable pageable);


    /**
     * How many of a citizen's reports sit in each status.
     *
     * The summary tiles above "My Reports" need one number per status. Asking
     * once per status would be three queries where one will do, so this
     * groups in the database and returns one row per status present.
     * Statuses the citizen has never used are simply absent from the result,
     * which the caller reads as zero.
     */
    @Query("""
            SELECT r.status AS status, COUNT(r) AS total
            FROM GarbageReport r
            WHERE r.user = :user
            GROUP BY r.status
            """)
    List<StatusCount> countByUserGroupedByStatus(@Param("user") User user);

    /**
     * One row of countByUserGroupedByStatus: a status and how many reports
     * carry it. A projection rather than an Object[], so the caller reads
     * named fields instead of remembering positions.
     */
    interface StatusCount {

        ReportStatus getStatus();

        long getTotal();
    }

    // Trending reports sorted by engagement score descending
    List<GarbageReport> findAllByOrderByEngagementScoreDesc();

    // Report having highest engagement score
    GarbageReport findTopByOrderByEngagementScoreDesc();

    // Average urgency score of all reports
    @Query("""
            SELECT AVG(r.urgencyScore)
            FROM GarbageReport r
            """)
    Double getAverageUrgencyScore();

    // Average engagement score of all reports
    @Query("""
            SELECT AVG(r.engagementScore)
            FROM GarbageReport r
            """)
    Double getAverageEngagementScore();


    /**
     * Counts reports having a specific status.
     */
    long countByStatus(ReportStatus status);


    /**
     * Number of reports created by a user.
     */
    long countByUser(User user);


    /**
     * Searches reports by title, city, state or pincode.
     */
    @Query("""
        SELECT r
        FROM GarbageReport r
        WHERE
              LOWER(r.title) LIKE LOWER(CONCAT('%', :keyword, '%'))
           OR LOWER(r.city) LIKE LOWER(CONCAT('%', :keyword, '%'))
           OR LOWER(r.state) LIKE LOWER(CONCAT('%', :keyword, '%'))
           OR LOWER(r.pincode) LIKE LOWER(CONCAT('%', :keyword, '%'))
        ORDER BY r.createdAt DESC
        """)
    List<GarbageReport> searchReports(@Param("keyword") String keyword);


    /**
     * Paged form of searchReports.
     *
     * The ORDER BY is gone from the query because the pageable supplies it -
     * carrying both would put a second sort after the LIMIT and defeat the
     * point of paging. The countQuery is written out rather than derived,
     * because Spring cannot infer one from this WHERE clause.
     *
     * Address is matched as well as title, city, state and pincode. That is
     * not an accident of this rewrite: the public register used to search
     * address on the client, over the full list it had downloaded. Now that
     * the search runs in the database, leaving address out would quietly
     * stop matching an address the register used to find.
     */
    @Query(
            value = """
                SELECT r
                FROM GarbageReport r
                WHERE
                      LOWER(r.title) LIKE LOWER(CONCAT('%', :keyword, '%'))
                   OR LOWER(r.city) LIKE LOWER(CONCAT('%', :keyword, '%'))
                   OR LOWER(r.state) LIKE LOWER(CONCAT('%', :keyword, '%'))
                   OR LOWER(r.pincode) LIKE LOWER(CONCAT('%', :keyword, '%'))
                   OR LOWER(r.address) LIKE LOWER(CONCAT('%', :keyword, '%'))
                """,
            countQuery = """
                SELECT COUNT(r)
                FROM GarbageReport r
                WHERE
                      LOWER(r.title) LIKE LOWER(CONCAT('%', :keyword, '%'))
                   OR LOWER(r.city) LIKE LOWER(CONCAT('%', :keyword, '%'))
                   OR LOWER(r.state) LIKE LOWER(CONCAT('%', :keyword, '%'))
                   OR LOWER(r.pincode) LIKE LOWER(CONCAT('%', :keyword, '%'))
                   OR LOWER(r.address) LIKE LOWER(CONCAT('%', :keyword, '%'))
                """
    )
    Page<GarbageReport> searchReportsPaged(@Param("keyword") String keyword, Pageable pageable);


    /**
     * Filters reports by optional status, city and state.
     */
    @Query("""
        SELECT r
        FROM GarbageReport r
        WHERE
            (CAST(:status AS string) IS NULL OR r.status = :status)
        AND
            (COALESCE(:city,'')='' OR LOWER(r.city)=LOWER(:city))
        AND
            (COALESCE(:state,'')='' OR LOWER(r.state)=LOWER(:state))
        ORDER BY r.createdAt DESC
        """)
    List<GarbageReport> filterReports(
            @Param("status") ReportStatus status,
            @Param("city") String city,
            @Param("state") String state
    );

    /**
     * Paged form of filterReports.
     *
     * The optional-parameter trick is kept exactly as the unpaged query had
     * it - a null status and a blank city both mean "no filter", and the
     * cast is what lets the database accept a null for the status
     * comparison at all.
     *
     * The reporter is fetched with LEFT JOIN FETCH for the same reason the
     * other paged finders use @EntityGraph: the mapper reads the name, and
     * a page of ten must not cost ten extra queries to resolve ten names.
     */
    @Query(
            value = """
                SELECT r
                FROM GarbageReport r
                LEFT JOIN FETCH r.user
                WHERE
                    (CAST(:status AS string) IS NULL OR r.status = :status)
                AND
                    (COALESCE(:city,'')='' OR LOWER(r.city)=LOWER(:city))
                AND
                    (COALESCE(:state,'')='' OR LOWER(r.state)=LOWER(:state))
                """,
            countQuery = """
                SELECT COUNT(r)
                FROM GarbageReport r
                WHERE
                    (CAST(:status AS string) IS NULL OR r.status = :status)
                AND
                    (COALESCE(:city,'')='' OR LOWER(r.city)=LOWER(:city))
                AND
                    (COALESCE(:state,'')='' OR LOWER(r.state)=LOWER(:state))
                """
    )
    Page<GarbageReport> filterReportsPaged(
            @Param("status") ReportStatus status,
            @Param("city") String city,
            @Param("state") String state,
            Pageable pageable
    );


    /**
     * Returns recent reports inside an approximate
     * latitude/longitude bounding box for duplicate detection.

     * Exact distance will be calculated later
     * using the Haversine formula.
     */
    @Query("""
        SELECT r
        FROM GarbageReport r
        WHERE
                r.pincode = :pincode
        AND
                r.latitude BETWEEN :minLatitude AND :maxLatitude
        AND
                r.longitude BETWEEN :minLongitude AND :maxLongitude
        AND
                r.createdAt >= :createdAfter
        """)
    List<GarbageReport> findNearbyRecentReports(

            @Param("pincode")
            String pincode,

            @Param("minLatitude")
            Double minLatitude,

            @Param("maxLatitude")
            Double maxLatitude,

            @Param("minLongitude")
            Double minLongitude,

            @Param("maxLongitude")
            Double maxLongitude,

            @Param("createdAfter")
            java.time.LocalDateTime createdAfter
    );
}