package com.cleanbharat.wastemanagement.repository;

import com.cleanbharat.wastemanagement.entity.MunicipalCorporation;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface MunicipalCorporationRepository extends JpaRepository<MunicipalCorporation, Long> {

    // Find municipal corporation by city name
    Optional<MunicipalCorporation> findByCityIgnoreCase(String city);

    /*
      The code-backed routing key: a report carrying a directory ULB code
      resolves its destination by this column, and only by this column - never
      by district, never by a free-text city name.
    */
    Optional<MunicipalCorporation> findByUlbCode(String ulbCode);

    /*
      One page of the admin register matching a keyword across the four
      fields the admin screen has always searched - city, organisation name,
      phone and email. The match runs in the database (LIKE on LOWER), so a
      keyword over the 350+ registered bodies costs a paged query rather
      than the whole table. An optional field that is NULL simply never
      matches, which is what the admin screen's own search did with nulls.
    */
    @Query(
            value = """
                SELECT mc
                FROM MunicipalCorporation mc
                WHERE
                      LOWER(mc.city) LIKE LOWER(CONCAT('%', :keyword, '%'))
                   OR LOWER(mc.organizationName) LIKE LOWER(CONCAT('%', :keyword, '%'))
                   OR LOWER(mc.phone) LIKE LOWER(CONCAT('%', :keyword, '%'))
                   OR LOWER(mc.email) LIKE LOWER(CONCAT('%', :keyword, '%'))
                """,
            countQuery = """
                SELECT COUNT(mc)
                FROM MunicipalCorporation mc
                WHERE
                      LOWER(mc.city) LIKE LOWER(CONCAT('%', :keyword, '%'))
                   OR LOWER(mc.organizationName) LIKE LOWER(CONCAT('%', :keyword, '%'))
                   OR LOWER(mc.phone) LIKE LOWER(CONCAT('%', :keyword, '%'))
                   OR LOWER(mc.email) LIKE LOWER(CONCAT('%', :keyword, '%'))
                """
    )
    Page<MunicipalCorporation> searchMunicipalCorporationsPaged(
            @Param("keyword") String keyword,
            Pageable pageable
    );

    // Guards the admin form against mapping two corporations to one ULB
    boolean existsByUlbCode(String ulbCode);

    // Same guard for updates, ignoring the row being edited itself
    boolean existsByUlbCodeAndIdNot(String ulbCode, Long id);

    /**
     * Looks a corporation up by its official email, which doubles as its login id.
     * Case-insensitive because people type "MCMohali@..." and "mcmohali@..." alike.
     */
    Optional<MunicipalCorporation> findByEmailIgnoreCase(String email);

    // Guards the admin form and the citizen sign-up form against reusing an official email
    boolean existsByEmailIgnoreCase(String email);
}
