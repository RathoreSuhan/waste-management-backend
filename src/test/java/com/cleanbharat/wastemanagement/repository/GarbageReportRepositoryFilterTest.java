package com.cleanbharat.wastemanagement.repository;

import com.cleanbharat.wastemanagement.entity.GarbageReport;
import com.cleanbharat.wastemanagement.enums.ReportStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Runs the real JPQL against an in-memory database, because the district
 * filter's meaning lives in the query itself: the state+district PAIR must
 * be matched, identically named districts in different states must never
 * mix, and a legacy row without a reconciled district must match no
 * district filter at all.
 */
@DataJpaTest
class GarbageReportRepositoryFilterTest {

    @Autowired
    private GarbageReportRepository repository;

    @BeforeEach
    void seedReports() {

        // Two ULBs of the same Bihar district
        repository.save(report("Overflowing bin near the temple", "Bodh Gaya", "Bihar", "Gaya"));
        repository.save(report("Construction debris on the bypass", "Gaya", "Bihar", "Gaya"));

        // The identical district NAME in a different state
        repository.save(report("Household waste at the market", "Aurangabad", "Maharashtra", "Aurangabad"));
        repository.save(report("Market waste near the bus stand", "Aurangabad", "Bihar", "Aurangabad"));

        // A legacy report filed before the district column existed
        repository.save(report("Plastic pile by the pond", "Patna", "Bihar", null));
    }

    private GarbageReport report(String title, String city, String state, String district) {
        return GarbageReport.builder()
                .title(title)
                .description("Seeded for the district-filter tests")
                .latitude(24.7)
                .longitude(85.0)
                .address("Test lane")
                .city(city)
                .state(state)
                .district(district)
                .pincode("823001")
                .status(ReportStatus.PENDING)
                .build();
    }

    @Test
    void theDistrictFilterMatchesOnlyItsOwnState() {
        // Both states have an "Aurangabad"; only Bihar's may answer the pair
        Page<GarbageReport> page = repository.filterReportsPaged(
                null, "Aurangabad", "Bihar", PageRequest.of(0, 10));

        assertEquals(1, page.getTotalElements());
        assertEquals("Bihar", page.getContent().get(0).getState());
    }

    @Test
    void theFilterIsCaseInsensitiveOnBothParts() {
        Page<GarbageReport> page = repository.filterReportsPaged(
                null, "aurangabad", "MAHARASHTRA", PageRequest.of(0, 10));

        assertEquals(1, page.getTotalElements());
        assertEquals("Maharashtra", page.getContent().get(0).getState());
    }

    @Test
    void theTotalCountIsIndependentOfThePageSlice() {
        // Two Bihar/Gaya rows exist; a page of one must still report both
        Page<GarbageReport> page = repository.filterReportsPaged(
                null, "Gaya", "Bihar", PageRequest.of(0, 1));

        assertEquals(2, page.getTotalElements());
        assertEquals(1, page.getContent().size());
        assertEquals(2, page.getTotalPages());
    }

    @Test
    void aLegacyReportWithoutAReconciledDistrictMatchesNoDistrictFilter() {
        // The Patna row has no district: it is never silently assigned one
        Page<GarbageReport> filtered = repository.filterReportsPaged(
                null, "Gaya", "Bihar", PageRequest.of(0, 10));
        assertTrue(filtered.getContent().stream()
                .allMatch(report -> "Gaya".equals(report.getDistrict())));

        // With no district filter it is still visible and searchable
        Page<GarbageReport> unfiltered = repository.filterReportsPaged(
                null, "", "", PageRequest.of(0, 10));
        assertEquals(5, unfiltered.getTotalElements());
    }

    @Test
    void keywordSearchMatchesTheDistrictToo() {
        // "aurangabad" appears in no title, city, state, pincode or address -
        // only the district column carries it, in both states
        Page<GarbageReport> page = repository.searchReportsPaged(
                "aurangabad", PageRequest.of(0, 10));

        assertEquals(2, page.getTotalElements());
    }
}
