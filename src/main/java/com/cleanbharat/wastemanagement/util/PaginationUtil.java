package com.cleanbharat.wastemanagement.util;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.Set;

/**
 * Turns loosely-typed paging parameters from a request into a Pageable.
 *
 * WHY THIS IS CENTRALISED
 *   Every paged endpoint accepts the same four values - page, size,
 *   sortBy, direction - and every one of them has to defend against the
 *   same two abuses: a size large enough to pull the whole table down in
 *   one request, and a sort field naming something that is not a column.
 *   Doing that per controller would mean the limits drifted apart, and the
 *   one that drifted would be the one nobody tested.
 *
 * SIZE LIMIT
 *   A page holds between 1 and MAX_PAGE_SIZE rows. Anything above is
 *   clamped rather than rejected, so an over-optimistic client still gets
 *   a usable response. A missing or nonsensical size (0, negative) falls
 *   back to DEFAULT_PAGE_SIZE.
 *
 * SORT WHITELIST
 *   `sortBy` is only honored when it names one of the properties the
 *   caller passes in. An unknown value falls back to the default rather
 *   than being interpolated into the query, which is what keeps a request
 *   parameter out of the ORDER BY clause.
 *
 * TIE-BREAK
 *   A sort is always given "id" as a secondary key. Without it, rows that
 *   share a timestamp - and reports filed in the same second do - have no
 *   defined order between them, so the same row can appear on two pages
 *   while another is skipped.
 */
public final class PaginationUtil {

    // Page size used whenever a request does not ask for one
    public static final int DEFAULT_PAGE_SIZE = 10;

    /*
      The largest page a caller may ask for.

      Set above every page size the portal itself uses, so the clamp only
      ever catches a deliberately large request and never quietly shortens
      a legitimate page.
    */
    public static final int MAX_PAGE_SIZE = 50;

    // Prevents object creation
    private PaginationUtil() {
    }

    /**
     * A page index below zero is meaningless and would make the database
     * skip a negative number of rows, so it is pulled back to the first
     * page.
     */
    public static int resolvePage(int page) {
        return Math.max(page, 0);
    }

    /**
     * A usable page size, clamped.
     */
    public static int resolveSize(int size) {

        if (size < 1) {
            return DEFAULT_PAGE_SIZE;
        }

        return Math.min(size, MAX_PAGE_SIZE);
    }

    /**
     * Pageable for a list with a fixed order, such as the public feed
     * whose query already carries its own ORDER BY.
     */
    public static Pageable resolve(int page, int size) {
        return PageRequest.of(resolvePage(page), resolveSize(size));
    }

    /**
     * Pageable over an explicit sort.
     */
    public static Pageable resolve(int page, int size, Sort sort) {
        return PageRequest.of(resolvePage(page), resolveSize(size), sort);
    }

    /**
     * Builds a sort from the request's sortBy/direction, falling back to
     * `defaultProperty` when the requested field is not allowed.
     *
     * `allowedProperties` is the whitelist. Callers list the properties
     * their entity actually exposes for ordering, so a misspelled or
     * hostile value can never reach the query.
     *
     * Direction is read case-insensitively and anything other than "asc"
     * is treated as descending - the newest-first order every list in the
     * portal wants by default.
     */
    public static Sort resolveSort(
            String sortBy,
            String direction,
            Set<String> allowedProperties,
            String defaultProperty
    ) {

        String property = sortBy != null && allowedProperties.contains(sortBy)
                ? sortBy
                : defaultProperty;

        Sort.Direction sortDirection = "asc".equalsIgnoreCase(direction)
                ? Sort.Direction.ASC
                : Sort.Direction.DESC;

        Sort primary = Sort.by(sortDirection, property);

        /*
          When the primary key is already the id there is nothing to break
          the tie on, and asking for it twice would produce "id, id".
        */
        if ("id".equals(property)) {
            return primary;
        }

        // Tie-break so rows sharing the primary value keep a stable order
        return primary.and(Sort.by(Sort.Direction.DESC, "id"));
    }
}
