package com.cleanbharat.wastemanagement.service;

import com.cleanbharat.wastemanagement.dto.MunicipalCorporationRequest;
import com.cleanbharat.wastemanagement.dto.MunicipalCorporationResponse;
import com.cleanbharat.wastemanagement.entity.MunicipalCorporation;
import com.cleanbharat.wastemanagement.exception.ResourceNotFoundException;
import com.cleanbharat.wastemanagement.repository.MunicipalCorporationRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service // Marks this class as a Spring Service Bean
@RequiredArgsConstructor // Constructor Injection
public class MunicipalCorporationServiceImpl implements MunicipalCorporationService {

    private final MunicipalCorporationRepository municipalCorporationRepository;

    @Override
    public MunicipalCorporationResponse createMunicipalCorporation(MunicipalCorporationRequest request) {

        MunicipalCorporation municipalCorporation = MunicipalCorporation.builder()
                        .city(request.getCity())
                        .organizationName(request.getOrganizationName())
                        .phone(request.getPhone())
                        .email(request.getEmail())
                        .build();

        MunicipalCorporation savedMunicipalCorporation = municipalCorporationRepository.save(municipalCorporation);

        return mapToResponse(savedMunicipalCorporation);
    }

    @Override
    public List<MunicipalCorporationResponse> getAllMunicipalCorporations() {

        return municipalCorporationRepository.findAll()
                .stream()
                .map(this::mapToResponse)
                .toList();
    }

    @Override
    public MunicipalCorporationResponse getByCity(String city) {

        MunicipalCorporation municipalCorporation = municipalCorporationRepository
                        .findByCityIgnoreCase(city)
                        .orElseThrow(() -> new ResourceNotFoundException(
                                "Municipal Corporation not found with city: " + city));

        return mapToResponse(municipalCorporation);
    }

    @Override
    public MunicipalCorporationResponse getById(Long id) {

        MunicipalCorporation municipalCorporation = municipalCorporationRepository
                        .findById(id)
                        .orElseThrow(() -> new ResourceNotFoundException(
                                "Municipal Corporation not found with id: " + id));

        return mapToResponse(municipalCorporation);
    }

    @Override
    public MunicipalCorporationResponse updateMunicipalCorporation(
            Long id,
            MunicipalCorporationRequest request) {

        MunicipalCorporation municipalCorporation = municipalCorporationRepository
                        .findById(id)
                        .orElseThrow(() -> new ResourceNotFoundException(
                                "Municipal Corporation not found with id: " + id));

        municipalCorporation.setCity(request.getCity());
        municipalCorporation.setOrganizationName(request.getOrganizationName());
        municipalCorporation.setPhone(request.getPhone());
        municipalCorporation.setEmail(request.getEmail());

        MunicipalCorporation updatedMunicipalCorporation = municipalCorporationRepository.save(municipalCorporation);

        return mapToResponse(updatedMunicipalCorporation);
    }

    @Override
    public void deleteMunicipalCorporation(Long id) {

        MunicipalCorporation municipalCorporation = municipalCorporationRepository
                        .findById(id)
                        .orElseThrow(() -> new ResourceNotFoundException(
                                "Municipal Corporation not found with id: " + id));

        municipalCorporationRepository.delete(municipalCorporation);
    }

    // Entity -> Response DTO
    private MunicipalCorporationResponse mapToResponse(MunicipalCorporation municipalCorporation) {

        return MunicipalCorporationResponse.builder()
                .id(municipalCorporation.getId())
                .city(municipalCorporation.getCity())
                .organizationName(municipalCorporation.getOrganizationName())
                .phone(municipalCorporation.getPhone())
                .email(municipalCorporation.getEmail())
                .build();
    }
}