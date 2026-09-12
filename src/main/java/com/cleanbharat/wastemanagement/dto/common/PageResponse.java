package com.cleanbharat.wastemanagement.dto.common;

import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

/**
 * One page of a list, plus the numbers a pager needs to describe itself.
 *
 * WHY THIS EXISTS
 *   The list endpoints used to return every matching row in a single
 *   array. That array is a plain JSON list with no room for metadata, so
 *   a reader could not be told how much was left - only handed the whole
 *   set. This envelope carries the rows for the requested page together
 *   with the totals, so the client can draw "Showing 11 to 20 of 47"
 *   without ever downloading all 47.
 *
 * PAGE NUMBERING
 *   `page` is zero-based, matching Spring's Pageable/Page convention.
 *   The portal's pager is one-based because that is what its buttons say,
 *   so the conversion happens in the frontend hook and nowhere else.
 *
 * `totalPages` is zero when nothing matches, and `hasNext`/`hasPrevious`
 * are derived here rather than left for the client to infer from the
 * numbers, so the two cannot disagree.
 */
public record PageResponse<T>(

        // The rows belonging to this page, already mapped to DTOs
        List<T> content,

        // Zero-based index of this page
        int page,

        // Maximum rows a page may hold
        int size,

        // Rows matching the request across every page
        long totalElements,

        // How many pages those rows fill
        int totalPages,

        // Whether a further page exists
        boolean hasNext,

        // Whether this is not the first page
        boolean hasPrevious
) {

    /**
     * Builds an envelope from a Spring Page, converting each entity to a
     * DTO as it is copied.
     *
     * The mapping happens here rather than in the service so that no
     * entity ever leaks towards the controller: the service hands over a
     * Page of entities and what comes out is a PageResponse of DTOs.
     *
     * `isLast`/`isFirst` come from the Page itself, so hasNext/hasPrevious
     * reflect what the database actually returned rather than a guess
     * computed from the totals.
     */
    public static <E, T> PageResponse<T> from(Page<E> page, Function<E, T> mapper) {

        List<T> content = page.getContent()
                .stream()
                .map(mapper)
                .toList();

        return new PageResponse<>(
                content,
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages(),
                !page.isLast(),
                !page.isFirst()
        );
    }
}
