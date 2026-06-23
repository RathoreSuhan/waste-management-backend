package com.cleanbharat.wastemanagement.controller;

import com.cleanbharat.wastemanagement.dto.MunicipalCorporationRequest;
import com.cleanbharat.wastemanagement.dto.MunicipalCorporationResponse;
import com.cleanbharat.wastemanagement.service.MunicipalCorporationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;
import java.util.List;

@RestController // Marks class as REST Controller
@RequestMapping("/api/municipal-corporations") // Base URL
@RequiredArgsConstructor // Constructor Injection
public class MunicipalCorporationController {

    private final MunicipalCorporationService municipalCorporationService;

    // Create Municipal Corporation
    @PostMapping
    public MunicipalCorporationResponse createMunicipalCorporation(@RequestBody MunicipalCorporationRequest request) {
        return municipalCorporationService.createMunicipalCorporation(request);
    }

    // Get All Municipal Corporations
    @GetMapping
    public List<MunicipalCorporationResponse> getAllMunicipalCorporations() {
        return municipalCorporationService.getAllMunicipalCorporations();
    }

    // Get By ID
    @GetMapping("/{id}")
    public MunicipalCorporationResponse getById(@PathVariable Long id) {
        return municipalCorporationService.getById(id);
    }

    // Get By City
    @GetMapping("/city/{city}")
    public MunicipalCorporationResponse getByCity(@PathVariable String city) {
        return municipalCorporationService.getByCity(city);
    }

    // Update Municipal Corporation
    @PutMapping("/{id}")
    public MunicipalCorporationResponse updateMunicipalCorporation(
            @PathVariable Long id,
            @RequestBody MunicipalCorporationRequest request) {

        return municipalCorporationService.updateMunicipalCorporation(id, request);
    }

    // Delete Municipal Corporation
    @DeleteMapping("/{id}")
    public String deleteMunicipalCorporation(@PathVariable Long id) {
        municipalCorporationService.deleteMunicipalCorporation(id);
        return "Municipal Corporation deleted successfully";
    }
}