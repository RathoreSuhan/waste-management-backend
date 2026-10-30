package com.cleanbharat.wastemanagement.repository;

import com.cleanbharat.wastemanagement.entity.MunicipalCorporation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Runs the real JPQL against an in-memory database, because the admin
 * register's keyword search lives in the query itself: all four visible
 * columns must match, case must not matter, and a row with an optional
 * field left NULL must neither break the search nor be matched by it.
 */
@DataJpaTest
class MunicipalCorporationRepositorySearchTest {

    @Autowired
    private MunicipalCorporationRepository repository;

    @BeforeEach
    void seedCorporations() {

        repository.save(corporation(
                "Mohali", "248325",
                "Municipal Corporation Mohali", "0172-123456", "contact@mohali.gov.in"));

        repository.save(corporation(
                "Patna", null,
                "Patna Municipal Corporation", "0612-222222", "info@patna.gov.in"));

        // A row whose optional contact fields were never filled in
        repository.save(corporation(
                "Gaya", null,
                "Gaya Nagar Nigam", null, null));
    }

    private MunicipalCorporation corporation(
            String city, String ulbCode, String organizationName, String phone, String email) {

        return MunicipalCorporation.builder()
                .city(city)
                .ulbCode(ulbCode)
                .organizationName(organizationName)
                .phone(phone)
                .email(email)
                .password("stored-hashed-never-returned")
                .build();
    }

    @Test
    void theKeywordMatchesEverySearchedColumn() {

        // organisation name
        assertEquals(1, repository
                .searchMunicipalCorporationsPaged("Nigam", PageRequest.of(0, 10))
                .getTotalElements());

        // phone number
        assertEquals(1, repository
                .searchMunicipalCorporationsPaged("0612", PageRequest.of(0, 10))
                .getTotalElements());

        // email
        assertEquals(1, repository
                .searchMunicipalCorporationsPaged("info@patna", PageRequest.of(0, 10))
                .getTotalElements());

        // city
        assertEquals(1, repository
                .searchMunicipalCorporationsPaged("mohali", PageRequest.of(0, 10))
                .getTotalElements());
    }

    @Test
    void theSearchIsCaseInsensitiveOnBothSides() {

        Page<MunicipalCorporation> page = repository.searchMunicipalCorporationsPaged(
                "MOHALI", PageRequest.of(0, 10));

        assertEquals(1, page.getTotalElements());
        assertEquals("Mohali", page.getContent().get(0).getCity());
    }

    @Test
    void theTotalCountIsIndependentOfThePageSlice() {

        // "corp" appears in two organisations; a page of one must still report both
        Page<MunicipalCorporation> page = repository.searchMunicipalCorporationsPaged(
                "municipal corp", PageRequest.of(0, 1));

        assertEquals(2, page.getTotalElements());
        assertEquals(1, page.getContent().size());
        assertEquals(2, page.getTotalPages());
    }

    @Test
    void aRowWithNullOptionalFieldsIsFoundByItsCityAndNeverByThem() {

        // Found through the column it does have
        Page<MunicipalCorporation> byCity = repository.searchMunicipalCorporationsPaged(
                "gaya", PageRequest.of(0, 10));
        assertEquals(1, byCity.getTotalElements());

        // The needle "0612" must stay on the Patna row - a NULL phone is not
        // silently read as a match
        Page<MunicipalCorporation> byPhone = repository.searchMunicipalCorporationsPaged(
                "0612", PageRequest.of(0, 10));
        assertTrue(byPhone.getContent().stream()
                .allMatch(corp -> "Patna".equals(corp.getCity())));
    }
}
