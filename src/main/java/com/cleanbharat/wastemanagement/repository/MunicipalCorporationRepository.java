package com.cleanbharat.wastemanagement.repository;

import com.cleanbharat.wastemanagement.entity.MunicipalCorporation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface MunicipalCorporationRepository extends JpaRepository<MunicipalCorporation, Long> {

    // Find municipal corporation by city name
    Optional<MunicipalCorporation> findByCityIgnoreCase(String city);
}