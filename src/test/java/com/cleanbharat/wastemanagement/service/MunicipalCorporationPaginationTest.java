package com.cleanbharat.wastemanagement.service;

import com.cleanbharat.wastemanagement.dto.MunicipalCorporationResponse;
import com.cleanbharat.wastemanagement.dto.common.PageResponse;
import com.cleanbharat.wastemanagement.entity.MunicipalCorporation;
import com.cleanbharat.wastemanagement.repository.MunicipalCorporationRepository;
import com.cleanbharat.wastemanagement.repository.UserRepository;
import com.cleanbharat.wastemanagement.service.location.IndianLocationService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the paged municipal-corporation register.
 *
 * Focus: the register defaults to newest first (id, the row's only
 * insertion-order proxy), routes a keyword through the database search
 * finder while a blank one stays on the plain paged finder, clamps the
 * page size exactly like the other registers, and maps the page's rows to
 * their response DTOs.
 */
@ExtendWith(MockitoExtension.class)
class MunicipalCorporationPaginationTest {

    @Mock
    private MunicipalCorporationRepository municipalCorporationRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @Mock
    private IndianLocationService indianLocationService;

    @InjectMocks
    private MunicipalCorporationServiceImpl municipalCorporationService;

    @Test
    void theRegisterDefaultsToNewestFirstById() {

        when(municipalCorporationRepository.findAll(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        municipalCorporationService.getAllMunicipalCorporations(null, 0, 10, null, null);

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(municipalCorporationRepository).findAll(pageableCaptor.capture());

        /*
          id is the primary sort, so resolveSort adds no second "id" tie-break
          row - the row carries no createdAt column to order by instead.
        */
        assertTrue(pageableCaptor.getValue().getSort().getOrderFor("id").isDescending());
        assertEquals(0, pageableCaptor.getValue().getPageNumber());
        assertEquals(10, pageableCaptor.getValue().getPageSize());
    }

    @Test
    void aKeywordRoutesThroughTheDatabaseSearchFinder() {

        when(municipalCorporationRepository.searchMunicipalCorporationsPaged(
                eq("mohali"), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        PageResponse<MunicipalCorporationResponse> response =
                municipalCorporationService.getAllMunicipalCorporations(
                        "  mohali  ", 0, 10, null, null);

        // The keyword is trimmed before it reaches the query
        verify(municipalCorporationRepository).searchMunicipalCorporationsPaged(
                eq("mohali"), any(Pageable.class));
        verify(municipalCorporationRepository, never()).findAll(any(Pageable.class));

        assertEquals(0, response.totalElements());
    }

    @Test
    void aBlankKeywordIsTheUnfilteredRegisterRatherThanASearch() {

        when(municipalCorporationRepository.findAll(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        municipalCorporationService.getAllMunicipalCorporations("   ", 0, 10, null, null);

        verify(municipalCorporationRepository).findAll(any(Pageable.class));
        verify(municipalCorporationRepository, never())
                .searchMunicipalCorporationsPaged(any(), any());
    }

    @Test
    void thePageSizeIsClampedLikeEveryOtherRegister() {

        when(municipalCorporationRepository.findAll(any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of()));

        municipalCorporationService.getAllMunicipalCorporations(null, 1, 999, null, null);

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(municipalCorporationRepository).findAll(pageableCaptor.capture());
        assertEquals(50, pageableCaptor.getValue().getPageSize());
    }

    @Test
    void pageRowsAreMappedToTheirResponseDtos() {

        MunicipalCorporation corporation = MunicipalCorporation.builder()
                .id(5L)
                .city("Mohali")
                .ulbCode("248325")
                .organizationName("Municipal Corporation Mohali")
                .phone("0172-123456")
                .email("contact@mohali.gov.in")
                .build();

        when(municipalCorporationRepository.findAll(any(Pageable.class)))
                .thenReturn(new PageImpl<>(
                        List.of(corporation),
                        Pageable.ofSize(10),
                        1));

        // The directory lookup answers null here: the display enrichment is
        // optional and must never break the register row itself
        when(indianLocationService.describeByUlbCode("248325", "Mohali"))
                .thenReturn(null);

        PageResponse<MunicipalCorporationResponse> response =
                municipalCorporationService.getAllMunicipalCorporations(null, 0, 10, null, null);

        assertEquals(1, response.totalElements());
        assertEquals(1, response.content().size());

        MunicipalCorporationResponse row = response.content().get(0);
        assertEquals("Mohali", row.getCity());
        assertEquals("248325", row.getUlbCode());
        assertEquals("Municipal Corporation Mohali", row.getOrganizationName());
        assertEquals("contact@mohali.gov.in", row.getEmail());
        assertNull(row.getMappedLocation());
    }
}
