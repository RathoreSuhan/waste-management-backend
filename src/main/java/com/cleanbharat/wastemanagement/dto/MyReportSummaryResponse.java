package com.cleanbharat.wastemanagement.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * The count tiles shown above a citizen's own reports.
 *
 * WHY THIS IS ITS OWN ENDPOINT
 *   The tiles used to be counted in the browser from the full list of the
 *   citizen's reports. Once that list is paged, the browser holds one page
 *   of ten and could only ever report "10 of everything" - the tiles would
 *   be wrong on every page but the first, and wrong on the first as soon
 *   as the citizen has more than ten reports.
 *
 *   Counting server-side keeps the tiles describing the whole collection
 *   while the list underneath them describes one page of it. The counts
 *   come from a single grouped query rather than one pass per status.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class MyReportSummaryResponse {

    // Every report the citizen has filed
    private long total;

    // Reports awaiting a cleanup team
    private long pending;

    // Reports claimed by a cleanup team
    private long inProgress;

    // Reports whose cleanup passed AI verification
    private long resolved;
}
