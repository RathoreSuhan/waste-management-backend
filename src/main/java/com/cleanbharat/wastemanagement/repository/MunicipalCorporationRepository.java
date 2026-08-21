package com.cleanbharat.wastemanagement.repository;

import com.cleanbharat.wastemanagement.entity.MunicipalCorporation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MunicipalCorporationRepository extends JpaRepository<MunicipalCorporation, Long> {

    // Find municipal corporation by city name
    Optional<MunicipalCorporation> findByCityIgnoreCase(String city);

    /**
     * Looks a corporation up by its official email, which doubles as its login id.
     * Case-insensitive because people type "MCMohali@..." and "mcmohali@..." alike.
     */
    Optional<MunicipalCorporation> findByEmailIgnoreCase(String email);

    // Guards the admin form and the citizen sign-up form against reusing an official email
    boolean existsByEmailIgnoreCase(String email);
}
