package com.cleanbharat.wastemanagement.service;

import com.cleanbharat.wastemanagement.dto.MunicipalCorporationRequest;
import com.cleanbharat.wastemanagement.dto.MunicipalCorporationResponse;

import java.util.List;

public interface MunicipalCorporationService {

    // Add new municipal corporation
    MunicipalCorporationResponse createMunicipalCorporation(MunicipalCorporationRequest request);

    // Get all municipal corporations
    List<MunicipalCorporationResponse> getAllMunicipalCorporations();

    // Get municipal corporation by city
    MunicipalCorporationResponse getByCity(String city);

    /*
      Get the corporation accountable for one exact LGD ULB code - the
      location-V2 lookup a code-backed report's contact panel uses. Never
      falls back to city-name matching: a code with no corporation is a
      clean 404, not a guess.
    */
    MunicipalCorporationResponse getByUlbCode(String ulbCode);

    // Get municipal corporation by id
    MunicipalCorporationResponse getById(Long id);

    // Get the corporation represented by the authenticated municipal officer
    MunicipalCorporationResponse getOwnCorporation(String email);

    // Update municipal corporation
    MunicipalCorporationResponse updateMunicipalCorporation(Long id, MunicipalCorporationRequest request);

    // Update only the corporation represented by the authenticated municipal officer
    MunicipalCorporationResponse updateOwnCorporation(String email, MunicipalCorporationRequest request);

    // Delete municipal corporation
    void deleteMunicipalCorporation(Long id);
}