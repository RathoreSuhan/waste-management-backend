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

    // Get municipal corporation by id
    MunicipalCorporationResponse getById(Long id);

    // Update municipal corporation
    MunicipalCorporationResponse updateMunicipalCorporation(Long id, MunicipalCorporationRequest request);

    // Delete municipal corporation
    void deleteMunicipalCorporation(Long id);
}