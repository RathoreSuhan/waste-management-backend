package com.cleanbharat.wastemanagement.controller;

import com.cleanbharat.wastemanagement.dto.MunicipalCorporationRequest;
import com.cleanbharat.wastemanagement.dto.MunicipalCorporationResponse;
import com.cleanbharat.wastemanagement.dto.MunicipalOfficerProfileRequest;
import com.cleanbharat.wastemanagement.dto.common.PageResponse;
import com.cleanbharat.wastemanagement.service.MunicipalCorporationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController // Marks class as REST Controller
@RequestMapping("/api/municipal-corporations") // Base URL
@RequiredArgsConstructor // Constructor Injection
public class MunicipalCorporationController {

    private final MunicipalCorporationService municipalCorporationService;

    // Create Municipal Corporation
    @PostMapping
    // @Valid runs the request limits; without it the DTO annotations never fire
    public MunicipalCorporationResponse createMunicipalCorporation(@Valid @RequestBody MunicipalCorporationRequest request) {
        return municipalCorporationService.createMunicipalCorporation(request);
    }

    /*
      One page of the admin register.

      The keyword searches city, organisation name, phone and email; the
      page slice and the search both run in the database. Every parameter
      is optional, so the plain GET an older client sends still resolves -
      it just receives the first page of 10 wrapped in the same PageResponse
      envelope the other admin registers already return.

      Example: /api/municipal-corporations?keyword=mohali&page=0&size=10
    */
    @GetMapping
    public ResponseEntity<PageResponse<MunicipalCorporationResponse>> getAllMunicipalCorporations(
            @RequestParam(required = false)
            String keyword,

            @RequestParam(defaultValue = "0")
            int page,

            @RequestParam(defaultValue = "10")
            int size,

            @RequestParam(required = false)
            String sortBy,

            @RequestParam(required = false)
            String direction
    ) {
        return ResponseEntity.ok(
                municipalCorporationService.getAllMunicipalCorporations(
                        keyword, page, size, sortBy, direction)
        );
    }

    /** Returns only the corporation belonging to the authenticated officer. */
    @GetMapping("/me")
    public MunicipalCorporationResponse getOwnMunicipalCorporation(Authentication authentication) {
        return municipalCorporationService.getOwnCorporation(authentication.getName());
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

    /*
      Get By ULB code - the location-V2 contact lookup. A report carrying a
      directory ULB code resolves its corporation through this exact match;
      there is no city-name variant of it, because a mapped corporation's
      stored city is its preserved legacy value and must stay that way.
    */
    @GetMapping("/ulb/{ulbCode}")
    public MunicipalCorporationResponse getByUlbCode(@PathVariable String ulbCode) {
        return municipalCorporationService.getByUlbCode(ulbCode);
    }

    // Update Municipal Corporation
    @PutMapping("/{id}")
    public MunicipalCorporationResponse updateMunicipalCorporation(
            @PathVariable Long id,
            @Valid @RequestBody MunicipalCorporationRequest request) {

        return municipalCorporationService.updateMunicipalCorporation(id, request);
    }

    /**
     * Updates only the contact details of the corporation belonging to the
     * authenticated officer. The request DTO carries no location fields, so
     * the stored legacy city and the administrator-assigned ULB mapping can
     * never change through this endpoint.
     */
    @PutMapping("/me")
    public MunicipalCorporationResponse updateOwnMunicipalCorporation(
            Authentication authentication,
            @Valid @RequestBody MunicipalOfficerProfileRequest request) {

        return municipalCorporationService.updateOwnCorporation(authentication.getName(), request);
    }

    // Delete Municipal Corporation
    @DeleteMapping("/{id}")
    public String deleteMunicipalCorporation(@PathVariable Long id) {
        municipalCorporationService.deleteMunicipalCorporation(id);
        return "Municipal Corporation deleted successfully";
    }
}