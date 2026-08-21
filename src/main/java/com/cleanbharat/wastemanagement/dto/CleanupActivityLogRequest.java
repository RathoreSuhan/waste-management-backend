package com.cleanbharat.wastemanagement.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.time.LocalDateTime;

/*
 * Payload for one optional activity entry logged during an IN_PROGRESS cleanup.
 *
 * Sent as multipart form fields because an entry may carry a progress image.
 * Only the description is mandatory - everything else is best-effort evidence.
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CleanupActivityLogRequest {

    // What was done on site
    @NotBlank(message = "Please describe the work you completed.")
    @Size(max = 1000, message = "Description cannot exceed 1000 characters.")
    private String description;

    /*
     * When the work happened.
     *
     * Optional: left null the server stamps "now", which keeps same-day logging
     * a single tap while still allowing yesterday's shift to be back-dated.
     */
    private LocalDateTime activityAt;

    // Optional device coordinates captured while writing the entry
    private Double latitude;

    private Double longitude;
}