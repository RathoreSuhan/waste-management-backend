package com.cleanbharat.wastemanagement.util;

import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit tests for the shared pagination rules.
 *
 * Focus: every paged endpoint clamps the same way and sorts through the
 * same whitelist, so a request can neither pull the whole table down in one
 * call nor smuggle a field name into an ORDER BY clause.
 */
class PaginationUtilTest {

    // Valid sort properties the register exposes, mirroring the services
    private static final Set<String> ALLOWED = Set.of("createdAt", "engagementScore");

    @Test
    void pageSizeDefaultsToTenWhenMissingOrNonsensical() {
        assertEquals(10, PaginationUtil.resolveSize(0));
        assertEquals(10, PaginationUtil.resolveSize(-3));
    }

    @Test
    void oversizedPageIsClampedRatherThanRejected() {
        assertEquals(PaginationUtil.MAX_PAGE_SIZE, PaginationUtil.resolveSize(999));
        assertEquals(PaginationUtil.MAX_PAGE_SIZE, PaginationUtil.resolveSize(PaginationUtil.MAX_PAGE_SIZE + 1));
    }

    @Test
    void validPageSizePassesThroughUntouched() {
        assertEquals(25, PaginationUtil.resolveSize(25));
    }

    @Test
    void negativePageIsPulledBackToZero() {
        assertEquals(0, PaginationUtil.resolvePage(-5));
        assertEquals(4, PaginationUtil.resolvePage(4));
    }

    @Test
    void resolveBuildsAPageableWithResolvedPageAndSize() {
        Pageable pageable = PaginationUtil.resolve(2, 999);

        assertEquals(2, pageable.getPageNumber());
        assertEquals(PaginationUtil.MAX_PAGE_SIZE, pageable.getPageSize());
    }

    @Test
    void unknownSortFallsBackToTheDefaultProperty() {
        Sort sort = PaginationUtil.resolveSort("title; DROP TABLE", "desc", ALLOWED, "createdAt");

        // The hostile value never reaches the query - only createdAt does
        assertTrue(sort.getOrderFor("createdAt").isDescending());
        assertEquals(null, sort.getOrderFor("title; DROP TABLE"));
    }

    @Test
    void allowedSortAndAscendingDirectionAreHonoured() {
        Sort sort = PaginationUtil.resolveSort("engagementScore", "asc", ALLOWED, "createdAt");

        assertTrue(sort.getOrderFor("engagementScore").isAscending());
    }

    @Test
    void nonAscDirectionMeansDescending() {
        Sort sort = PaginationUtil.resolveSort("engagementScore", "banana", ALLOWED, "createdAt");

        assertTrue(sort.getOrderFor("engagementScore").isDescending());
    }

    @Test
    void tieBreakOnIdKeepsRowsWithSharedValueStable() {
        Sort sort = PaginationUtil.resolveSort("engagementScore", "desc", ALLOWED, "createdAt");

        // Rows sharing a score still have a defined order between them
        assertTrue(sort.getOrderFor("id").isDescending());
    }

    @Test
    void nullSortByFallsBackToDefaultWithoutFailing() {
        // sortBy is omitted by well-behaved clients; it must not throw
        Sort sort = PaginationUtil.resolveSort(null, "desc", ALLOWED, "createdAt");

        assertTrue(sort.getOrderFor("createdAt").isDescending());
    }

    @Test
    void pageRequestCarriesTheResolvedSort() {
        Pageable pageable = PaginationUtil.resolve(
                0,
                10,
                PaginationUtil.resolveSort("engagementScore", "desc", ALLOWED, "createdAt")
        );

        assertTrue(((PageRequest) pageable).getSort().getOrderFor("engagementScore").isDescending());
        assertTrue(((PageRequest) pageable).getSort().getOrderFor("id").isDescending());
    }
}